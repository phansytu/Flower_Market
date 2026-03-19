package com.flowermarketplace.notification.repository;

import com.flowermarketplace.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.user.id = :userId AND n.read = false")
    int markAllAsRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.id = :id AND n.user.id = :userId")
    int markOneAsRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Purge old read notifications to keep the table lean (scheduled job). */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.read = true AND n.createdAt < :cutoff")
    int deleteOldRead(@Param("cutoff") LocalDateTime cutoff);
}
