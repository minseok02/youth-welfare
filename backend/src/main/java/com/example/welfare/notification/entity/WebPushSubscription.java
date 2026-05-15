package com.example.welfare.notification.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "web_push_subscriptions", indexes = {
        @Index(name = "idx_wps_user_key_enabled_created", columnList = "user_key, enabled, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WebPushSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 32)
    private String userKey;

    @Column(nullable = false, length = 500, unique = true)
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth_secret", nullable = false, length = 255)
    private String authSecret;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_label", length = 100)
    private String deviceLabel;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    private LocalDateTime lastSeenAt;

    private LocalDateTime lastSentAt;

    private LocalDateTime lastErrorAt;

    @Column(length = 500)
    private String lastErrorMessage;

    public void refresh(String userKey,
                        String p256dh,
                        String authSecret,
                        String userAgent,
                        String deviceLabel) {
        this.userKey = userKey;
        this.p256dh = p256dh;
        this.authSecret = authSecret;
        this.userAgent = userAgent;
        this.deviceLabel = deviceLabel;
        this.enabled = true;
        this.lastSeenAt = LocalDateTime.now();
        this.lastErrorAt = null;
        this.lastErrorMessage = null;
    }

    public void markSent() {
        this.enabled = true;
        this.lastSentAt = LocalDateTime.now();
        this.lastErrorAt = null;
        this.lastErrorMessage = null;
    }

    public void markError(String errorMessage) {
        this.lastErrorAt = LocalDateTime.now();
        this.lastErrorMessage = trimErrorMessage(errorMessage);
    }

    public void disable(String errorMessage) {
        this.enabled = false;
        this.lastErrorAt = LocalDateTime.now();
        this.lastErrorMessage = trimErrorMessage(errorMessage);
    }

    private String trimErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage;
    }
}
