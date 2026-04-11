package com.earzuhal.Repository;

import com.earzuhal.Model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Contract> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<Contract> findByStatusOrderByCreatedAtDesc(String status);

    long countByUserIdAndStatus(Long userId, String status);

    long countByUserId(Long userId);

    /** Karşı tarafın TC Kimlik numarasına göre sözleşmeleri bul (onay routing) */
    List<Contract> findByCounterpartyTcKimlikAndStatusOrderByCreatedAtDesc(String counterpartyTcKimlik, String status);
}
