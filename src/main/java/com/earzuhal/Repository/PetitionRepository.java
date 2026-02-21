package com.earzuhal.Repository;

import com.earzuhal.Model.Petition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PetitionRepository extends JpaRepository<Petition, Long> {

    List<Petition> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Petition> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, String status);
}
