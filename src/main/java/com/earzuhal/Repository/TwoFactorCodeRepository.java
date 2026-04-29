package com.earzuhal.Repository;

import com.earzuhal.Model.TwoFactorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface TwoFactorCodeRepository extends JpaRepository<TwoFactorCode, Long> {

    Optional<TwoFactorCode> findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(Long userId, String purpose);

    @Modifying
    @Transactional
    @Query("UPDATE TwoFactorCode t SET t.used = true WHERE t.userId = :userId AND t.purpose = :purpose AND t.used = false")
    void invalidateActive(@Param("userId") Long userId, @Param("purpose") String purpose);
}
