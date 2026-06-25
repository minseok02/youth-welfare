package com.example.welfare.user.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_pii_sync_queue",
        indexes = {
                @Index(name = "idx_upsq_status_enqueued", columnList = "status,last_enqueued_at"),
                @Index(name = "idx_upsq_status_synced", columnList = "status,last_synced_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserPiiSyncQueue extends BaseTimeEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String userKey;

    private String emailEnc;

    private String nameEnc;

    private String birthDateEnc;

    private String phoneEnc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserPiiSyncQueueStatus status = UserPiiSyncQueueStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    private LocalDateTime lastEnqueuedAt;

    private LocalDateTime lastAttemptAt;

    private LocalDateTime lastSyncedAt;

    @Column(length = 500)
    private String lastError;

    public void enqueue(String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        this.emailEnc = emailEnc;
        this.nameEnc = nameEnc;
        this.birthDateEnc = birthDateEnc;
        this.phoneEnc = phoneEnc;
        this.status = UserPiiSyncQueueStatus.PENDING;
        this.lastEnqueuedAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markSynced() {
        LocalDateTime now = LocalDateTime.now();
        this.status = UserPiiSyncQueueStatus.SYNCED;
        this.attemptCount += 1;
        this.lastAttemptAt = now;
        this.lastSyncedAt = now;
        this.lastError = null;
    }

    public void markFailed(String errorMessage) {
        this.status = UserPiiSyncQueueStatus.FAILED;
        this.attemptCount += 1;
        this.lastAttemptAt = LocalDateTime.now();
        this.lastError = trimErrorMessage(errorMessage);
    }

    public void replaceEncryptedPayload(String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        this.emailEnc = emailEnc;
        this.nameEnc = nameEnc;
        this.birthDateEnc = birthDateEnc;
        this.phoneEnc = phoneEnc;
    }

    private String trimErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "unknown error";
        }
        return errorMessage.length() > MAX_ERROR_LENGTH
                ? errorMessage.substring(0, MAX_ERROR_LENGTH)
                : errorMessage;
    }
}
