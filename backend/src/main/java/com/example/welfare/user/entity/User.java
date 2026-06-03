package com.example.welfare.user.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 32, insertable = false, updatable = false)
    private String userKey;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private AccountOrigin accountOrigin = AccountOrigin.REAL_USER;

    @Column(nullable = false)
    private String passwordHash;

    private String name;

    private LocalDate birthDate;

    private String phoneEnc; // AES-256 암호화

    private String sido;

    private String sgg;

    private String regionCode; // 온통청년 지역코드

    private Byte incomeLevel; // 1~10분위

    private String householdType;

    private String employmentStatus;

    private String houseTenureCode;

    private String housingTypeCode;

    private String basicLivingRecipientTypeCode;

    private String disabilityGradeCode;

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean notificationYn = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean notificationEmailYn = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean notificationInAppYn = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean notificationWebPushYn = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationPeriod notificationPeriod = NotificationPeriod.NONE;

    @Builder.Default
    private Double notificationMinScore = 0.5;

    private LocalDateTime notificationConsentAt;

    @Column(nullable = false)
    @Builder.Default
    private int loginFailCount = 0;

    private LocalDateTime lockedUntil;

    @Column(nullable = false)
    @Builder.Default
    private int displayCount = 10;

    @Builder.Default
    private int profileCompleteness = 0;

    private LocalDateTime withdrawnAt;

    // === 비즈니스 메서드 ===

    public void increaseLoginFailCount() {
        this.loginFailCount++;
    }

    public void lock(LocalDateTime until) {
        this.lockedUntil = until;
    }

    public void resetLoginFail() {
        this.loginFailCount = 0;
        this.lockedUntil = null;
    }

    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    public void updateProfile(String sido, String sgg,
                               String regionCode, Byte incomeLevel, String householdType,
                               String employmentStatus, String houseTenureCode,
                               String housingTypeCode, String basicLivingRecipientTypeCode,
                               String disabilityGradeCode, int displayCount) {
        this.sido = sido;
        this.sgg = sgg;
        this.regionCode = regionCode;
        this.incomeLevel = incomeLevel;
        this.householdType = householdType;
        this.employmentStatus = employmentStatus;
        this.houseTenureCode = houseTenureCode;
        this.housingTypeCode = housingTypeCode;
        this.basicLivingRecipientTypeCode = basicLivingRecipientTypeCode;
        this.disabilityGradeCode = disabilityGradeCode;
        this.displayCount = displayCount;
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updatePhone(String phoneEnc) {
        this.phoneEnc = phoneEnc;
    }

    public void updateProfileCompleteness(int completeness) {
        this.profileCompleteness = completeness;
    }

    public void updateNotification(boolean notificationYn,
                                   boolean notificationEmailYn,
                                   boolean notificationInAppYn,
                                   boolean notificationWebPushYn,
                                   NotificationPeriod period,
                                   Double minScore,
                                   LocalDateTime consentAt) {
        this.notificationYn = notificationYn;
        this.notificationEmailYn = notificationEmailYn;
        this.notificationInAppYn = notificationInAppYn;
        this.notificationWebPushYn = notificationWebPushYn;
        this.notificationPeriod = period;
        this.notificationMinScore = minScore;
        this.notificationConsentAt = consentAt;
    }

    public void unsubscribeNotifications() {
        this.notificationYn = false;
        this.notificationEmailYn = false;
        this.notificationInAppYn = false;
        this.notificationWebPushYn = false;
        this.notificationPeriod = NotificationPeriod.NONE;
    }

    public void withdraw() {
        this.email = "withdrawn_" + this.id;
        this.passwordHash = "withdrawn";
        this.name = null;
        this.birthDate = null;
        this.phoneEnc = null;
        this.sido = null;
        this.sgg = null;
        this.regionCode = null;
        this.incomeLevel = null;
        this.householdType = null;
        this.employmentStatus = null;
        this.houseTenureCode = null;
        this.housingTypeCode = null;
        this.basicLivingRecipientTypeCode = null;
        this.disabilityGradeCode = null;
        this.notificationYn = false;
        this.notificationEmailYn = false;
        this.notificationInAppYn = false;
        this.notificationWebPushYn = false;
        this.notificationConsentAt = null;
        this.isActive = false;
        this.withdrawnAt = LocalDateTime.now();
    }

    public enum NotificationPeriod {
        DAILY, WEEKLY, NONE
    }

    public enum AccountOrigin {
        EXAMPLE_SMOKE,
        BOUNDED_LOCAL,
        LOCAL_REAL_NON_EXAMPLE_SEED,
        REAL_USER
    }
}
