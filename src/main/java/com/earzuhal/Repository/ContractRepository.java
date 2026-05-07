package com.earzuhal.Repository;

import com.earzuhal.Model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Contract> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<Contract> findByStatusOrderByCreatedAtDesc(String status);

    long countByUserIdAndStatus(Long userId, String status);

    long countByUserId(Long userId);

    List<Contract> findByCounterpartyTcKimlikAndStatusOrderByCreatedAtDesc(String counterpartyTcKimlik, String status);

    List<Contract> findByCounterpartyTcKimlikOrderByCreatedAtDesc(String counterpartyTcKimlik);

    /**
     * Soft-deleted bir kullanıcının sözleşmelerini, aynı TC ile yeniden kayıt
     * olan yeni kullanıcıya devret. VerificationService kimlik doğrulama
     * sırasında çağırır.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE contracts SET user_id = :newUserId WHERE user_id = :oldUserId", nativeQuery = true)
    int transferOwnership(@Param("oldUserId") Long oldUserId, @Param("newUserId") Long newUserId);
}
