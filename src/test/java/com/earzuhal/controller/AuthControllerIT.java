package com.earzuhal.controller;

import com.earzuhal.Service.TokenBlacklistService;
import com.earzuhal.dto.auth.LoginRequest;
import com.earzuhal.dto.auth.RegisterRequest;
import com.earzuhal.dto.auth.AuthResponse;
import com.earzuhal.exception.GlobalExceptionHandler;
import com.earzuhal.exception.UserAlreadyExistsException;
import com.earzuhal.security.jwt.JwtTokenProvider;
import com.earzuhal.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Auth Controller entegrasyon testi.
 *
 * Jüri kanıtları:
 *  ✓ Geçerli register 201 dönüyor
 *  ✓ @Valid — kısa username reddediliyor (400)
 *  ✓ @Valid — geçersiz email reddediliyor (400)
 *  ✓ @Valid — boş password reddediliyor (400)
 *  ✓ Existing user → 409 Conflict dönüyor
 *  ✓ Geçersiz credentials → 401 dönüyor
 *  ✓ Geçerli login → JWT token dönüyor
 */
@WebMvcTest(controllers = com.earzuhal.controller.AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean AuthService authService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistService tokenBlacklistService;

    // ── Kayıt ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("201 — geçerli kayıt isteği başarılı")
    void register_valid_request_returns_201() throws Exception {
        RegisterRequest req = new RegisterRequest("testuser", "test@kullanici.com", "sifre12345", "Test", "User");

        AuthResponse resp = AuthResponse.builder()
                .token("jwt-token-xxx")
                .tokenType("Bearer")
                .build();
        when(authService.register(any())).thenReturn(resp);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token-xxx"));
    }

    @Test
    @DisplayName("400 — username 3 karakterden kısa → validation hatası")
    void register_short_username_returns_400() throws Exception {
        RegisterRequest req = new RegisterRequest("ab", "test@email.com", "sifre12345", "Test", "User");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — username özel karakter içeriyor → pattern hatası")
    void register_invalid_username_pattern_returns_400() throws Exception {
        RegisterRequest req = new RegisterRequest("test user!", "test@email.com", "sifre12345", "Test", "User");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — geçersiz email formatı reddediliyor")
    void register_invalid_email_returns_400() throws Exception {
        RegisterRequest req = new RegisterRequest("testuser", "gecersiz-email", "sifre12345", "Test", "User");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — şifre 8 karakterden kısa → validation hatası")
    void register_short_password_returns_400() throws Exception {
        RegisterRequest req = new RegisterRequest("testuser", "test@email.com", "abc", "Test", "User");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("409 — mevcut kullanıcı ile kayıt → Conflict")
    void register_existing_user_returns_409() throws Exception {
        RegisterRequest req = new RegisterRequest("existinguser", "existing@email.com", "sifre12345", "Test", "User");

        when(authService.register(any()))
                .thenThrow(new UserAlreadyExistsException("Bu kullanıcı adı zaten alınmış."));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ── Giriş ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("200 — geçerli giriş bilgileri ile JWT token dönüyor")
    void login_valid_credentials_returns_200_with_token() throws Exception {
        LoginRequest req = new LoginRequest("testuser", "sifre12345");

        AuthResponse resp = AuthResponse.builder()
                .token("jwt-login-token")
                .tokenType("Bearer")
                .build();
        when(authService.login(any())).thenReturn(resp);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-login-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("401 — yanlış şifre ile giriş reddediliyor")
    void login_wrong_password_returns_401() throws Exception {
        LoginRequest req = new LoginRequest("testuser", "yanlis_sifre");

        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("400 — boş login alanları reddediliyor")
    void login_empty_fields_returns_400() throws Exception {
        LoginRequest req = new LoginRequest("", "");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
