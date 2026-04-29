package com.earzuhal.controller;

import com.earzuhal.service.TokenBlacklistService;
import com.earzuhal.dto.auth.AuthResponse;
import com.earzuhal.dto.auth.ForgotPasswordRequest;
import com.earzuhal.dto.auth.LoginRequest;
import com.earzuhal.dto.auth.RegisterRequest;
import com.earzuhal.dto.auth.ResetPasswordRequest;
import com.earzuhal.dto.auth.TwoFactorVerifyRequest;
import com.earzuhal.security.jwt.JwtTokenProvider;
import com.earzuhal.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthController(AuthService authService,
                          JwtTokenProvider jwtTokenProvider,
                          TokenBlacklistService tokenBlacklistService) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        AuthResponse response = authService.register(registerRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    /** Mevcut token'ı sunucu tarafında geçersiz kılar (sadece bu cihazın token'ı) */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            try {
                String jti = jwtTokenProvider.getJtiFromToken(token);
                OffsetDateTime expiresAt = jwtTokenProvider.getExpirationFromToken(token)
                        .toInstant()
                        .atOffset(ZoneOffset.UTC);
                tokenBlacklistService.revoke(jti, expiresAt);
            } catch (Exception ignored) {
                // Token zaten geçersiz ya da süresi dolmuş — yine de başarılı say
            }
        }
        return ResponseEntity.ok(Map.of("message", "Başarıyla çıkış yapıldı"));
    }

    /** Şifre sıfırlama bağlantısı talep eder. E-posta enumerasyonunu engellemek için her zaman 200 döner. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authService.requestPasswordReset(request.getEmail());
        } catch (Exception ignored) {
            // sessizce yut
        }
        return ResponseEntity.ok(Map.of(
                "message", "Eğer bu e-posta adresiyle bir hesabınız varsa, sıfırlama bağlantısı gönderildi."
        ));
    }

    /** Sıfırlama tokenı ile yeni şifre belirler. */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Şifreniz başarıyla güncellendi."));
    }

    /** Giriş yapmış kullanıcıya e-posta ile 6 haneli doğrulama kodu gönderir. */
    @PostMapping("/2fa/send")
    public ResponseEntity<Map<String, String>> send2fa(@RequestParam(value = "action", required = false) String action) {
        String username = currentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Bu işlem için giriş yapmalısınız."));
        }
        authService.send2faCode(username, action);
        return ResponseEntity.ok(Map.of("message", "Doğrulama kodu e-posta adresinize gönderildi."));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<Map<String, String>> verify2fa(@Valid @RequestBody TwoFactorVerifyRequest request) {
        String username = currentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Bu işlem için giriş yapmalısınız."));
        }
        authService.verify2faCode(username, request.getCode(), request.getAction());
        return ResponseEntity.ok(Map.of("message", "Doğrulama başarılı."));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
