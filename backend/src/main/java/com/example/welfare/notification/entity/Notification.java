package com.example.welfare.notification.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import com.example.welfare.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_noti_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_noti_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    public enum NotificationChannel {
        EMAIL, KAKAO
    }

    public enum NotificationPeriodType {
        DAILY, WEEKLY, MANUAL
    }

    public enum NotificationStatus {
        SENT, FAILED
    }
}
