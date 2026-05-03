package com.earzuhal.security.jwt;

import com.earzuhal.config.JwtConfig;
import com.earzuhal.exception.InvalidTokenException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtConfig jwtConfig;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.secretKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateTokenFromUsername(userDetails.getUsername(), userDetails);
    }

    public String generateToken(UserDetails userDetails) {
        return generateTokenFromUsername(userDetails.getUsername(), userDetails);
    }

    private String generateTokenFromUsername(String username, UserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getExpirationMs());

        String roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())   // jti claim — revocation için
                .subject(username)
                .claim("roles", roles)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Refresh token üretir. Access token'dan farkı:
     *  - "type" claim'i "refresh"
     *  - daha uzun ömür (jwt.refresh-expiration-ms)
     *  - rol claim'i içermez (yalnızca yeni access token üretmek için kullanılır)
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getRefreshExpirationMs());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public boolean isRefreshToken(String token) {
        Object type = getClaims(token).get("type");
        return "refresh".equals(type);
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getJtiFromToken(String token) {
        return getClaims(token).getId();
    }

    public Date getExpirationFromToken(String token) {
        return getClaims(token).getExpiration();
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            logger.error("Expired JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty: {}", ex.getMessage());
            throw new InvalidTokenException("JWT claims string is empty");
        }
    }
}
