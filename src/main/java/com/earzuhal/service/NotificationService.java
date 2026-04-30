package com.earzuhal.service;

import com.earzuhal.Model.Notification;
import com.earzuhal.Model.NotificationDeviceToken;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.NotificationDeviceTokenRepository;
import com.earzuhal.Repository.NotificationRepository;
import com.earzuhal.dto.notification.NotificationResponse;
import com.earzuhal.dto.notification.RegisterDeviceTokenRequest;
import com.earzuhal.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeviceTokenRepository tokenRepository;
    private final UserService userService;
    private final PushNotificationService pushNotificationService;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationDeviceTokenRepository tokenRepository,
                               UserService userService,
                               PushNotificationService pushNotificationService) {
        this.notificationRepository = notificationRepository;
        this.tokenRepository = tokenRepository;
        this.userService = userService;
        this.pushNotificationService = pushNotificationService;
    }

    @Transactional
    public void registerDeviceToken(String username, RegisterDeviceTokenRequest request) {
        User user = userService.getUserByUsernameOrEmail(username);

        NotificationDeviceToken token = tokenRepository.findByToken(request.getToken())
                .orElseGet(NotificationDeviceToken::new);

        token.setUser(user);
        token.setToken(request.getToken());
        token.setPlatform(request.getPlatform());
        token.setIsActive(true);
        token.setUpdatedAt(OffsetDateTime.now());
        token.setLastSeenAt(OffsetDateTime.now());
        if (token.getCreatedAt() == null) {
            token.setCreatedAt(OffsetDateTime.now());
        }

        tokenRepository.save(token);
    }

    public List<NotificationResponse> getMyNotifications(String username, boolean unreadOnly) {
        User user = userService.getUserByUsernameOrEmail(username);
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId())
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return notifications.stream()
                .limit(50)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Long> getUnreadCount(String username) {
        User user = userService.getUserByUsernameOrEmail(username);
        long count = notificationRepository.countByUserIdAndIsReadFalse(user.getId());
        return Map.of("unreadCount", count);
    }

    @Transactional
    public void markAsRead(String username, Long notificationId) {
        User user = userService.getUserByUsernameOrEmail(username);
        int updated = notificationRepository.markReadByIdForUser(notificationId, user.getId());
        if (updated == 0) {
            // Kayıt yoksa veya bu kullanıcıya ait değilse 404 dön (IDOR önleme)
            boolean exists = notificationRepository.findById(notificationId).isPresent();
            if (!exists) {
                throw new ResourceNotFoundException("Bildirim bulunamadı, id: " + notificationId);
            }
            // Var ama başka kullanıcıya ait — yine 404 dön
            throw new ResourceNotFoundException("Bildirim bulunamadı, id: " + notificationId);
        }
    }

    @Transactional
    public void markAllAsRead(String username) {
        User user = userService.getUserByUsernameOrEmail(username);
        notificationRepository.markAllReadForUser(user.getId());
    }

    @Transactional
    public void notifyUser(User targetUser, String type, String title, String message, Long contractId) {
        Notification notification = new Notification();
        notification.setUser(targetUser);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setContractId(contractId);
        notification.setIsRead(false);
        notification.setCreatedAt(OffsetDateTime.now());

        notificationRepository.save(notification);
        pushNotificationService.sendToUser(targetUser, title, message, contractId);
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .contractId(notification.getContractId())
                .read(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
