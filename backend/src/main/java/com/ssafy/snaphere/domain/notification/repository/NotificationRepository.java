package com.ssafy.snaphere.domain.notification.repository;

import com.ssafy.snaphere.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 정렬에 PK 를 붙여 동시각 알림의 페이지 경계를 고정한다. */
    Page<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.recipientId = :userId AND n.read = false")
    int markAllRead(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.recipientId = :userId AND n.id IN :ids AND n.read = false")
    int markRead(@Param("userId") Long userId, @Param("ids") java.util.List<Long> ids);
}
