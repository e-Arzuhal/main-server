package com.earzuhal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String TWO_FA_VERIFY_PATH = "/api/auth/2fa/verify";
    private static final String TWO_FA_SEND_PATH = "/api/auth/2fa/send";

    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Deque<Instant>> loginAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> registerAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> twoFaVerifyAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> twoFaSendAttempts = new ConcurrentHashMap<>();

    @Value("${auth.rate-limit.login.max-attempts:10}")
    private int loginMaxAttempts;

    @Value("${auth.rate-limit.login.window-seconds:60}")
    private int loginWindowSeconds;

    @Value("${auth.rate-limit.register.max-attempts:5}")
    private int registerMaxAttempts;

    @Value("${auth.rate-limit.register.window-seconds:3600}")
    private int registerWindowSeconds;

    /** 2FA brute-force koruması: 6 haneli kod = 10^6 olasılık, kısa pencerede agresif limit. */
    @Value("${auth.rate-limit.2fa-verify.max-attempts:5}")
    private int twoFaVerifyMaxAttempts;

    @Value("${auth.rate-limit.2fa-verify.window-seconds:300}")
    private int twoFaVerifyWindowSeconds;

    /** 2FA kod isteme: e-posta gönderim spam'ini engelle. */
    @Value("${auth.rate-limit.2fa-send.max-attempts:3}")
    private int twoFaSendMaxAttempts;

    @Value("${auth.rate-limit.2fa-send.window-seconds:300}")
    private int twoFaSendWindowSeconds;

    @Value("${security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    public AuthRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return !LOGIN_PATH.equals(path)
                && !REGISTER_PATH.equals(path)
                && !TWO_FA_VERIFY_PATH.equals(path)
                && !TWO_FA_SEND_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        String clientKey = path + ":" + resolveClientIp(request);

        boolean limited;
        if (LOGIN_PATH.equals(path)) {
            limited = isRateLimited(loginAttempts, clientKey, loginMaxAttempts, loginWindowSeconds);
        } else if (REGISTER_PATH.equals(path)) {
            limited = isRateLimited(registerAttempts, clientKey, registerMaxAttempts, registerWindowSeconds);
        } else if (TWO_FA_VERIFY_PATH.equals(path)) {
            limited = isRateLimited(twoFaVerifyAttempts, clientKey, twoFaVerifyMaxAttempts, twoFaVerifyWindowSeconds);
        } else {
            limited = isRateLimited(twoFaSendAttempts, clientKey, twoFaSendMaxAttempts, twoFaSendWindowSeconds);
        }

        if (limited) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("detail", "Çok fazla istek gönderildi. Lütfen daha sonra tekrar deneyin.")
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(ConcurrentHashMap<String, Deque<Instant>> attempts,
                                  String key,
                                  int maxAttempts,
                                  int windowSeconds) {
        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(windowSeconds);

        Deque<Instant> window = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(threshold)) {
                window.pollFirst();
            }
            if (window.size() >= maxAttempts) {
                return true;
            }
            window.addLast(now);
        }

        return false;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (!trustForwardedHeaders) {
            return request.getRemoteAddr();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
