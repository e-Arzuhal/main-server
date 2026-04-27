package com.earzuhal.service;

import com.earzuhal.Model.NotificationDeviceToken;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.NotificationDeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final String EXPO_RECEIPTS_URL = "https://exp.host/--/api/v2/push/getReceipts";
    private static final String EXPO_STATUS_OK = "ok";
    private static final String EXPO_DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

    private final NotificationDeviceTokenRepository tokenRepository;
    private final WebClient webClient;

    public PushNotificationService(NotificationDeviceTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
        this.webClient = WebClient.builder().build();
    }

    public void sendToUser(User user, String title, String body, Long contractId) {
        List<NotificationDeviceToken> tokens = tokenRepository.findByUserIdAndIsActiveTrue(user.getId());
        if (tokens.isEmpty()) {
            return;
        }

        List<String> receiptIds = new ArrayList<>();

        for (NotificationDeviceToken token : tokens) {
            try {
                Map<String, Object> payload = Map.of(
                        "to", token.getToken(),
                        "title", title,
                        "body", body,
                        "sound", "default",
                        "data", Map.of(
                                "contractId", contractId == null ? "" : String.valueOf(contractId),
                                "type", "contract_notification"
                        )
                );

                Map<?, ?> response = webClient.post()
                        .uri(EXPO_PUSH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                token.setLastSeenAt(OffsetDateTime.now());
                token.setUpdatedAt(OffsetDateTime.now());
                tokenRepository.save(token);

                String ticketId = parseAndHandleTicket(response, token, user.getId());
                if (ticketId != null && !ticketId.isBlank()) {
                    receiptIds.add(ticketId);
                }
            } catch (Exception e) {
                log.warn("Push bildirimi gönderilemedi userId={}, tokenId={}: {}",
                        user.getId(), token.getId(), e.getMessage());
            }
        }

        if (!receiptIds.isEmpty()) {
            checkReceipts(receiptIds, user.getId());
        }
    }

    private String parseAndHandleTicket(Map<?, ?> response, NotificationDeviceToken token, Long userId) {
        if (response == null) {
            return null;
        }

        Object dataObj = response.get("data");
        if (!(dataObj instanceof List<?> dataList) || dataList.isEmpty() || !(dataList.get(0) instanceof Map<?, ?> ticket)) {
            return null;
        }

        String status = Objects.toString(ticket.get("status"), "");
        if (!EXPO_STATUS_OK.equalsIgnoreCase(status)) {
            deactivateIfDeviceUnregistered(ticket, token, userId);
            return null;
        }

        return Objects.toString(ticket.get("id"), null);
    }

    private void checkReceipts(List<String> receiptIds, Long userId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("ids", receiptIds);

            Map<?, ?> response = webClient.post()
                    .uri(EXPO_RECEIPTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !(response.get("data") instanceof Map<?, ?> data)) {
                return;
            }

            for (Object value : data.values()) {
                if (value instanceof Map<?, ?> receipt) {
                    handleReceipt(receipt, userId);
                }
            }
        } catch (Exception e) {
            log.warn("Push receipt kontrolü başarısız userId={}: {}", userId, e.getMessage());
        }
    }

    private void handleReceipt(Map<?, ?> receipt, Long userId) {
        String status = Objects.toString(receipt.get("status"), "");
        if (EXPO_STATUS_OK.equalsIgnoreCase(status)) {
            return;
        }

        if (!(receipt.get("details") instanceof Map<?, ?> details)) {
            return;
        }

        String error = Objects.toString(details.get("error"), "");
        if (!EXPO_DEVICE_NOT_REGISTERED.equals(error)) {
            return;
        }

        Object tokenValue = details.get("expoPushToken");
        if (tokenValue == null) {
            return;
        }

        String expoPushToken = tokenValue.toString();
        tokenRepository.findByToken(expoPushToken).ifPresent(token -> {
            token.setIsActive(false);
            token.setUpdatedAt(OffsetDateTime.now());
            tokenRepository.save(token);
            log.info("Geçersiz Expo token pasifleştirildi userId={}, tokenId={}", userId, token.getId());
        });
    }

    private void deactivateIfDeviceUnregistered(Map<?, ?> ticket, NotificationDeviceToken token, Long userId) {
        if (!(ticket.get("details") instanceof Map<?, ?> details)) {
            return;
        }

        String error = Objects.toString(details.get("error"), "");
        if (!EXPO_DEVICE_NOT_REGISTERED.equals(error)) {
            return;
        }

        token.setIsActive(false);
        token.setUpdatedAt(OffsetDateTime.now());
        tokenRepository.save(token);
        log.info("Geçersiz Expo token ticket aşamasında pasifleştirildi userId={}, tokenId={}", userId, token.getId());
    }
}
