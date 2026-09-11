package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    void markAllAsReadByUserId(Long userId);

    Optional<Notification> findByUserIdAndTypeAndReferenceId(Long userId, String type, Long referenceId);

    Optional<Notification> findByUserIdAndTypeAndReferenceIdAndCreatedAtAfter(Long userId, String type, Long referenceId, LocalDateTime after);
}
