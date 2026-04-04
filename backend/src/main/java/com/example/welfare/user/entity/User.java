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

    @Column(nullable = false, unique = true)
    private String email;

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

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean notificationYn = false;

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

    public void updateProfile(String name, LocalDate birthDate, String sido, String sgg,
                               String regionCode, Byte incomeLevel, String householdType,
                               String employmentStatus, int displayCount) {
        this.name = name;
        this.birthDate = birthDate;
        this.sido = sido;
        this.sgg = sgg;
        this.regionCode = regionCode;
        this.incomeLevel = incomeLevel;
        this.householdType = householdType;
        this.employmentStatus = employmentStatus;
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

    public void updateNotification(boolean notificationYn, NotificationPeriod period,
                                    Double minScore, LocalDateTime consentAt) {
        this.notificationYn = notificationYn;
        this.notificationPeriod = period;
        this.notificationMinScore = minScore;
        this.notificationConsentAt = consentAt;
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
        this.notificationYn = false;
        this.notificationConsentAt = null;
        this.isActive = false;
        this.withdrawnAt = LocalDateTime.now();
    }

    public enum NotificationPeriod {
        DAILY, WEEKLY, NONE
    }
}
