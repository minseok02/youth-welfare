package com.example.welfare.user.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String userKey;

    private Integer age;

    @Column(length = 20)
    private String ageBand;

    private LocalDateTime ageCalculatedAt;

    private String sido;

    private String sgg;

    private String regionCode;

    private Byte incomeLevel;

    private String householdType;

    private String employmentStatus;

    @Column(nullable = false)
    private boolean notificationYn;

    @Column(nullable = false)
    private boolean notificationEmailYn;

    @Column(nullable = false)
    private boolean notificationInAppYn;

    @Column(nullable = false)
    private boolean notificationWebPushYn;

    @Enumerated(EnumType.STRING)
    private User.NotificationPeriod notificationPeriod;

    private Double notificationMinScore;

    private LocalDateTime notificationConsentAt;

    @Column(nullable = false)
    private int displayCount;

    private int profileCompleteness;

    @Column(nullable = false)
    private boolean hasName;

    @Column(nullable = false)
    private boolean hasBirthDate;

    @Column(nullable = false)
    private boolean hasPhone;

    public void syncFrom(User user,
                         Integer age,
                         String ageBand,
                         LocalDateTime ageCalculatedAt,
                         boolean hasName,
                         boolean hasBirthDate) {
        this.age = age;
        this.ageBand = ageBand;
        this.ageCalculatedAt = ageCalculatedAt;
        this.sido = user.getSido();
        this.sgg = user.getSgg();
        this.regionCode = user.getRegionCode();
        this.incomeLevel = user.getIncomeLevel();
        this.householdType = user.getHouseholdType();
        this.employmentStatus = user.getEmploymentStatus();
        this.notificationYn = user.isNotificationYn();
        this.notificationEmailYn = user.isNotificationEmailYn();
        this.notificationInAppYn = user.isNotificationInAppYn();
        this.notificationWebPushYn = user.isNotificationWebPushYn();
        this.notificationPeriod = user.getNotificationPeriod();
        this.notificationMinScore = user.getNotificationMinScore();
        this.notificationConsentAt = user.getNotificationConsentAt();
        this.displayCount = user.getDisplayCount();
        this.profileCompleteness = user.getProfileCompleteness();
        this.hasName = hasName;
        this.hasBirthDate = hasBirthDate;
        this.hasPhone = user.getPhoneEnc() != null && !user.getPhoneEnc().trim().isEmpty();
    }
}
