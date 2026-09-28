package com.shoplocker.fssai.repository;

import com.shoplocker.fssai.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndIsInBinFalseOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndIsInBinTrueOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalseAndIsInBinFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false AND n.isInBin = false")
    void markAllAsReadByUserId(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isInBin = true WHERE n.user.id = :userId")
    void moveNotificationsToBin(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isInBin = true WHERE n.id = :id AND n.user.id = :userId AND n.isInBin = false")
    int moveNotificationToBin(Long userId, Long id);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.user.id = :userId AND n.isInBin = true")
    int deleteFromBin(Long userId, Long id);

    @Modifying
    @Query("UPDATE Notification n SET n.isInBin = false WHERE n.id = :id AND n.user.id = :userId AND n.isInBin = true")
    int restoreFromBin(Long userId, Long id);

    @Modifying
    @Query("UPDATE Notification n SET n.isInBin = false WHERE n.user.id = :userId AND n.isInBin = true")
    void restoreAllFromBin(Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.isInBin = true")
    void permanentDeleteNotifications(Long userId);

    Optional<Notification> findByUserIdAndTypeAndReferenceId(Long userId, String type, Long referenceId);

    Optional<Notification> findByUserIdAndTypeAndReferenceIdAndCreatedAtAfter(Long userId, String type, Long referenceId, Instant after);

    boolean existsByUserIdAndTypeAndReferenceIdAndMetadataAndCreatedAtAfter(Long userId, String type,
                                                                           Long referenceId, String metadata,
                                                                           Instant after);

    long countByUserIdAndTypeAndReferenceIdAndCreatedAtAfter(Long userId, String type, Long referenceId, Instant after);
}
