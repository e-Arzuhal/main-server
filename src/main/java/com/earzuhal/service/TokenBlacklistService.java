package com.earzuhal.service;

import com.earzuhal.Model.RevokedToken;
import com.earzuhal.Repository.RevokedTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;

    public TokenBlacklistService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Transactional
    public void revoke(String jti, OffsetDateTime expiresAt) {
        RevokedToken revoked = new RevokedToken();
        revoked.setJti(jti);
        revoked.setExpiresAt(expiresAt);
        revoked.setRevokedAt(OffsetDateTime.now());
        revokedTokenRepository.save(revoked);
    }

    public boolean isRevoked(String jti) {
        return revokedTokenRepository.existsByJti(jti);
    }

    /** Süresi dolmuş revoke kayıtlarını her gece 03:00'da temizle */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpired() {
        revokedTokenRepository.deleteExpiredTokens(OffsetDateTime.now());
    }
}
