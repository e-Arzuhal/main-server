package com.earzuhal.Repository;

import com.earzuhal.Model.NotificationDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationDeviceTokenRepository extends JpaRepository<NotificationDeviceToken, Long> {
    Optional<NotificationDeviceToken> findByToken(String token);
    List<NotificationDeviceToken> findByUserIdAndIsActiveTrue(Long userId);
}
