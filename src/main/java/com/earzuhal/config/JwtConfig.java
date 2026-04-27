package com.earzuhal.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {

    /** HS256 minimum: 256 bit (32 byte). Daha kısa anahtarlar brute-force riskini artırır. */
    private static final int MIN_SECRET_BYTES = 32;

    private String secret;
    private Long expirationMs;
    private Long refreshExpirationMs;

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret tanımlı değil. JWT_SECRET ortam değişkenini ayarlayın.");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret çok kısa (" + bytes + " bayt). HS256 için en az "
                            + MIN_SECRET_BYTES + " bayt (256 bit) gereklidir.");
        }
        if (expirationMs == null || expirationMs <= 0) {
            throw new IllegalStateException("jwt.expiration-ms pozitif olmalı.");
        }
    }
}
