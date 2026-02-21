package com.earzuhal.Repository;

import com.earzuhal.Model.IdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {

    Optional<IdentityVerification> findByUserId(Long userId);
}
