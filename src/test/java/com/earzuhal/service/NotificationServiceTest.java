package com.earzuhal.service;

import com.earzuhal.Model.Notification;
import com.earzuhal.Model.NotificationDeviceToken;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.NotificationDeviceTokenRepository;
import com.earzuhal.Repository.NotificationRepository;
import com.earzuhal.dto.notification.RegisterDeviceTokenRequest;
import com.earzuhal.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDeviceTokenRepository tokenRepository;

    @Mock
    private UserService userService;

    @Mock
    private PushNotificationService pushNotificationService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                tokenRepository,
                userService,
                pushNotificationService
        );
    }

    @Test
    void registerDeviceToken_createsAndSavesToken() {
        User user = new User();
        user.setId(10L);

        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest();
        request.setToken("ExponentPushToken[test-token]");
        request.setPlatform("android");

        when(userService.getUserByUsernameOrEmail("alice")).thenReturn(user);
        when(tokenRepository.findByToken(request.getToken())).thenReturn(Optional.empty());

        notificationService.registerDeviceToken("alice", request);

        ArgumentCaptor<NotificationDeviceToken> captor = ArgumentCaptor.forClass(NotificationDeviceToken.class);
        verify(tokenRepository).save(captor.capture());

        NotificationDeviceToken saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals("ExponentPushToken[test-token]", saved.getToken());
        assertEquals("android", saved.getPlatform());
        assertEquals(true, saved.getIsActive());
    }

    @Test
    void getUnreadCount_returnsRepositoryCount() {
        User user = new User();
        user.setId(22L);

        when(userService.getUserByUsernameOrEmail("bob")).thenReturn(user);
        when(notificationRepository.countByUserIdAndIsReadFalse(22L)).thenReturn(4L);

        Map<String, Long> result = notificationService.getUnreadCount("bob");

        assertEquals(4L, result.get("unreadCount"));
    }

    @Test
    void markAsRead_whenNotificationBelongsToAnotherUser_throwsNotFound() {
        User actor = new User();
        actor.setId(1L);

        User owner = new User();
        owner.setId(2L);

        Notification notification = new Notification();
        notification.setId(99L);
        notification.setUser(owner);
        notification.setIsRead(false);

        when(userService.getUserByUsernameOrEmail("eve")).thenReturn(actor);
        when(notificationRepository.findById(99L)).thenReturn(Optional.of(notification));

        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAsRead("eve", 99L));
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
