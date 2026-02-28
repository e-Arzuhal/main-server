package com.earzuhal.controller;

import com.earzuhal.Service.TokenBlacklistService;
import com.earzuhal.dto.auth.AuthResponse;
import com.earzuhal.dto.auth.LoginRequest;
import com.earzuhal.dto.auth.RegisterRequest;
import com.earzuhal.security.jwt.JwtTokenProvider;
import com.earzuhal.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
