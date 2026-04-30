package com.earzuhal.Repository;

import com.earzuhal.Model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndIsReadFalse(Long userId);

    /** Tek seferde tüm okunmamış bildirimleri okundu yapar (toplu update). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.user.id = :userId and (n.isRead = false or n.isRead is null)")
    int markAllReadForUser(@Param("userId") Long userId);

    /** Tek bildirimi okundu yapar (yetki kontrolüyle). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.id = :id and n.user.id = :userId")
    int markReadByIdForUser(@Param("id") Long id, @Param("userId") Long userId);
}
