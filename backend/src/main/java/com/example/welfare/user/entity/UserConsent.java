package com.example.welfare.user.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_consents",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_consents_user_type", columnNames = {"user_key", "consent_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 32)
    private String userKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Builder
    private UserConsent(String userKey, ConsentType consentType, LocalDateTime agreedAt, LocalDateTime withdrawnAt) {
        this.userKey = userKey;
        this.consentType = consentType;
        this.agreedAt = agreedAt;
        this.withdrawnAt = withdrawnAt;
    }

    public static UserConsent agree(String userKey, ConsentType consentType, LocalDateTime agreedAt) {
        return UserConsent.builder()
                .userKey(userKey)
                .consentType(consentType)
                .agreedAt(agreedAt)
                .build();
    }

    public boolean isActive() {
        return withdrawnAt == null;
    }

    public void agreeAgain(LocalDateTime agreedAt) {
        this.agreedAt = agreedAt;
        this.withdrawnAt = null;
    }

    public void withdraw(LocalDateTime withdrawnAt) {
        this.withdrawnAt = withdrawnAt;
    }

    public enum ConsentType {
        PRIVACY_NOTICE,
        OPTIONAL_PROFILE,
        SENSITIVE_INFO
    }
}
