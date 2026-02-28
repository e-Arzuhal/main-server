package com.earzuhal.Repository;

import com.earzuhal.Model.DisclaimerAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DisclaimerRepository extends JpaRepository<DisclaimerAcceptance, Long> {

    boolean existsByUserIdAndVersion(Long userId, String version);

    Optional<DisclaimerAcceptance> findFirstByUserIdAndVersionOrderByAcceptedAtDesc(Long userId, String version);
}
