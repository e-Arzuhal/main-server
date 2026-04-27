package com.earzuhal.controller;

import com.earzuhal.service.NotificationService;
import com.earzuhal.dto.notification.NotificationResponse;
import com.earzuhal.dto.notification.RegisterDeviceTokenRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        String username = authentication.getName();
        return ResponseEntity.ok(notificationService.getMyNotifications(username, unreadOnly));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(notificationService.getUnreadCount(username));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String username = authentication.getName();
        notificationService.markAsRead(username, id);
        return ResponseEntity.ok(Map.of("message", "Bildirim okundu olarak işaretlendi"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(Authentication authentication) {
        String username = authentication.getName();
        notificationService.markAllAsRead(username);
        return ResponseEntity.ok(Map.of("message", "Tüm bildirimler okundu olarak işaretlendi"));
    }

    @PostMapping("/device-token")
    public ResponseEntity<Map<String, String>> registerDeviceToken(
            Authentication authentication,
            @Valid @RequestBody RegisterDeviceTokenRequest request
    ) {
        String username = authentication.getName();
        notificationService.registerDeviceToken(username, request);
        return ResponseEntity.ok(Map.of("message", "Cihaz token kaydedildi"));
    }
}
