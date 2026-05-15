package com.example.welfare.notification.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_alerts", indexes = {
        @Index(name = "idx_ua_user_key_created", columnList = "user_key, created_at"),
        @Index(name = "idx_ua_user_key_status_created", columnList = "user_key, status, created_at"),
        @Index(name = "uq_user_alert_event_key", columnList = "event_key", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAlert extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 32)
    private String userKey;

    @Column(name = "event_key", length = 120, unique = true)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserAlertKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserAlertStatus status;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "deeplink_url", length = 500)
    private String deeplinkUrl;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    private LocalDateTime readAt;

    private LocalDateTime hiddenAt;

    public void markRead() {
        if (this.status == UserAlertStatus.HIDDEN) {
            return;
        }
        this.status = UserAlertStatus.READ;
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }

    public void hide() {
        this.status = UserAlertStatus.HIDDEN;
        this.hiddenAt = LocalDateTime.now();
        if (this.readAt == null) {
            this.readAt = this.hiddenAt;
        }
    }

    public enum UserAlertKind {
        RECOMMENDATION_DIGEST,
        DEADLINE_REMINDER,
        SYSTEM
    }

    public enum UserAlertStatus {
        UNREAD,
        READ,
        HIDDEN
    }
}
