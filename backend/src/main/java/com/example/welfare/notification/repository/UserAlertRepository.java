package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.UserAlert;
import com.example.welfare.notification.entity.UserAlert.UserAlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserAlertRepository extends JpaRepository<UserAlert, Long> {

    List<UserAlert> findTop30ByUserKeyAndStatusNotOrderByCreatedAtDesc(String userKey, UserAlertStatus status);

    long countByUserKeyAndStatus(String userKey, UserAlertStatus status);

    Optional<UserAlert> findByIdAndUserKey(Long id, String userKey);

    Optional<UserAlert> findByEventKey(String eventKey);

    boolean existsByUserKeyAndKindAndStatusAndTitleAndDeeplinkUrl(
            String userKey,
            UserAlert.UserAlertKind kind,
            UserAlertStatus status,
            String title,
            String deeplinkUrl
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserAlert ua
               set ua.status = com.example.welfare.notification.entity.UserAlert$UserAlertStatus.HIDDEN,
                   ua.hiddenAt = :hiddenAt,
                   ua.readAt = coalesce(ua.readAt, :hiddenAt)
             where ua.status = com.example.welfare.notification.entity.UserAlert$UserAlertStatus.UNREAD
               and ua.kind = :kind
               and ua.title = :title
               and coalesce(ua.deeplinkUrl, '') = :deeplinkUrl
               and ua.createdAt < :cutoff
            """)
    int hideUnreadByKindAndTitleAndDeeplinkUrlBefore(
            @Param("kind") UserAlert.UserAlertKind kind,
            @Param("title") String title,
            @Param("deeplinkUrl") String deeplinkUrl,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("hiddenAt") LocalDateTime hiddenAt
    );
}
