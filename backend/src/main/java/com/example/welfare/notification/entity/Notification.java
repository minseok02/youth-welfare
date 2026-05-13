package com.example.welfare.notification.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_noti_user_key_created", columnList = "user_key, created_at"),
        @Index(name = "idx_noti_status_created", columnList = "status, created_at"),
        @Index(name = "uq_noti_dispatch_key", columnList = "dispatch_key", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 32)
    private String userKey;

    @Column(name = "dispatch_key", length = 80, unique = true)
    private String dispatchKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationPeriodType periodType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalServices = 0;

    private LocalDateTime sentAt;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    private LocalDateTime nextRetryAt;

    public void reserveDispatch() {
        this.status = NotificationStatus.PENDING;
        this.sentAt = null;
        this.errorMessage = null;
        this.retryCount = 0;
        this.nextRetryAt = null;
        this.totalServices = 0;
        this.messageText = null;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.errorMessage = null;
        this.nextRetryAt = null;
    }

    public void scheduleRetry(LocalDateTime nextRetryAt, String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.retryCount = this.retryCount + 1;
        this.nextRetryAt = nextRetryAt;
        this.errorMessage = errorMessage;
    }

    public void failInitially(LocalDateTime nextRetryAt, String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.retryCount = 0;
        this.nextRetryAt = nextRetryAt;
        this.errorMessage = errorMessage;
    }

    public void updateDispatchPayload(String messageText, int totalServices) {
        this.messageText = messageText;
        this.totalServices = totalServices;
    }

    public enum NotificationChannel {
        // Existing MySQL enum columns store lowercase literals.
        email,
        kakao;

        public static final NotificationChannel EMAIL = email;
        public static final NotificationChannel KAKAO = kakao;
    }

    public enum NotificationPeriodType {
        daily,
        weekly,
        manual;

        public static final NotificationPeriodType DAILY = daily;
        public static final NotificationPeriodType WEEKLY = weekly;
        public static final NotificationPeriodType MANUAL = manual;
    }

    public enum NotificationStatus {
        pending,
        sent,
        failed;

        public static final NotificationStatus PENDING = pending;
        public static final NotificationStatus SENT = sent;
        public static final NotificationStatus FAILED = failed;
    }
}
