package com.earzuhal.controller;

import com.earzuhal.Service.NotificationService;
import com.earzuhal.dto.notification.NotificationResponse;
import com.earzuhal.dto.notification.RegisterDeviceTokenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private MockMvc mockMvc;

    private NotificationService notificationService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        notificationService = mock(NotificationService.class);
        objectMapper = new ObjectMapper();
        Class<?> controllerClass = Class.forName("com.earzuhal.controller.NotificationController");
        Object controller = controllerClass
                .getConstructor(NotificationService.class)
                .newInstance(notificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private Authentication auth(String username) {
        return new TestingAuthenticationToken(username, "n/a");
    }

    @Test
    void getMyNotifications_returnsItemsAndPassesUnreadFlag() throws Exception {
        NotificationResponse response = NotificationResponse.builder()
                .id(7L)
                .type("CONTRACT_PENDING_APPROVAL")
                .title("Yeni onay")
                .message("Onay bekleyen sozlesme var")
                .contractId(44L)
                .read(false)
                .createdAt(OffsetDateTime.parse("2026-04-18T12:00:00Z"))
                .build();

        when(notificationService.getMyNotifications(eq("alice"), eq(true))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/notifications")
                        .param("unreadOnly", "true")
                        .principal(auth("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7L))
                .andExpect(jsonPath("$[0].title").value("Yeni onay"))
                .andExpect(jsonPath("$[0].read").value(false));

        verify(notificationService).getMyNotifications("alice", true);
    }

    @Test
    void markAllAsRead_returnsOkAndCallsService() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all")
                        .principal(auth("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tüm bildirimler okundu olarak işaretlendi"));

        verify(notificationService).markAllAsRead("bob");
    }

    @Test
    void registerDeviceToken_validPayload_callsService() throws Exception {
        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest(
                "ExponentPushToken[test-token]",
                "android"
        );

        mockMvc.perform(post("/api/notifications/device-token")
                        .principal(auth("charlie"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cihaz token kaydedildi"));

        verify(notificationService).registerDeviceToken(eq("charlie"), eq(request));
    }

    @Test
    void getUnreadCount_returnsPayload() throws Exception {
        when(notificationService.getUnreadCount("alice")).thenReturn(Map.of("unreadCount", 3L));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .principal(auth("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3L));

        verify(notificationService).getUnreadCount("alice");
    }
}
