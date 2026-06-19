package com.example.welfare.user.dto.response;

import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.UserAttributeReadModel;
import com.example.welfare.user.repository.UserPriorityReadModel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class ProfileResponse {

    private Long id;
    private String email;
    private String name;
    private LocalDate birthDate;
    private String sido;
    private String sgg;
    private String regionCode;
    private Byte incomeLevel;
    private String ageBand;
    private String householdType;
    private String employmentStatus;
    private String houseTenureCode;
    private String housingTypeCode;
    private String basicLivingRecipientTypeCode;
    private String disabilityGradeCode;
    private boolean notificationYn;
    private boolean notificationEmailYn;
    private boolean notificationInAppYn;
    private boolean notificationWebPushYn;
    private String notificationPeriod;
    private Double notificationMinScore;
    private LocalDateTime notificationConsentAt;
    private int displayCount;
    private int profileCompleteness;
    private boolean hasPhone;
    private boolean optionalProfileConsentAgreed;
    private boolean sensitiveInfoConsentAgreed;
    private List<String> interestFields;
    private List<String> targetTypes;
    private List<PriorityItem> priorities;

    @Getter
    @Builder
    public static class PriorityItem {
        private int rank;
        private String code;
        private double weight;
    }

    public static ProfileResponse of(User user, List<UserAttribute> attributes,
                                      List<UserPriority> priorities) {
        List<String> interestFields = attributes.stream()
                .filter(a -> UserAttribute.AttrType.INTEREST_FIELD.name().equals(a.getAttrType()))
                .map(UserAttribute::getAttrValue)
                .collect(Collectors.toList());
        List<String> targetTypes = attributes.stream()
                .filter(a -> UserAttribute.AttrType.TARGET_TYPE.name().equals(a.getAttrType()))
                .map(UserAttribute::getAttrValue)
                .collect(Collectors.toList());

        List<PriorityItem> priorityItems = priorities.stream()
                .map(p -> PriorityItem.builder()
                        .rank(p.getPriorityRank())
                        .code(p.getPriorityOption().getCode())
                        .weight(p.getWeight())
                        .build())
                .collect(Collectors.toList());

        int profileCompleteness = calculateCompleteness(
                user.getName(),
                user.getBirthDate(),
                user.getSido(),
                user.getIncomeLevel(),
                user.getEmploymentStatus(),
                user.getHouseholdType(),
                interestFields,
                priorityItems
        );

        return ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .birthDate(user.getBirthDate())
                .sido(user.getSido())
                .sgg(user.getSgg())
                .regionCode(user.getRegionCode())
                .incomeLevel(user.getIncomeLevel())
                .ageBand(null)
                .householdType(user.getHouseholdType())
                .employmentStatus(user.getEmploymentStatus())
                .houseTenureCode(user.getHouseTenureCode())
                .housingTypeCode(user.getHousingTypeCode())
                .basicLivingRecipientTypeCode(user.getBasicLivingRecipientTypeCode())
                .disabilityGradeCode(user.getDisabilityGradeCode())
                .notificationYn(user.isNotificationYn())
                .notificationEmailYn(user.isNotificationEmailYn())
                .notificationInAppYn(user.isNotificationInAppYn())
                .notificationWebPushYn(user.isNotificationWebPushYn())
                .notificationPeriod(user.getNotificationPeriod().name())
                .notificationMinScore(user.getNotificationMinScore())
                .notificationConsentAt(user.getNotificationConsentAt())
                .displayCount(user.getDisplayCount())
                .profileCompleteness(profileCompleteness)
                .hasPhone(user.getPhoneEnc() != null && !user.getPhoneEnc().trim().isEmpty())
                .optionalProfileConsentAgreed(false)
                .sensitiveInfoConsentAgreed(false)
                .interestFields(interestFields)
                .targetTypes(targetTypes)
                .priorities(priorityItems)
                .build();
    }

    public static ProfileResponse of(Long userId, String email, String name, LocalDate birthDate,
                                     UserProfile profile,
                                     List<UserAttributeReadModel> attributes,
                                     List<UserPriorityReadModel> priorities,
                                     boolean optionalProfileConsentAgreed,
                                     boolean sensitiveInfoConsentAgreed) {
        List<String> interestFields = attributes.stream()
                .filter(a -> UserAttribute.AttrType.INTEREST_FIELD.name().equals(a.getAttrType()))
                .map(UserAttributeReadModel::getAttrValue)
                .collect(Collectors.toList());
        List<String> targetTypes = attributes.stream()
                .filter(a -> UserAttribute.AttrType.TARGET_TYPE.name().equals(a.getAttrType()))
                .map(UserAttributeReadModel::getAttrValue)
                .collect(Collectors.toList());

        List<PriorityItem> priorityItems = priorities.stream()
                .map(p -> PriorityItem.builder()
                        .rank(p.getPriorityRank())
                        .code(p.getCode())
                        .weight(p.getWeight())
                        .build())
                .collect(Collectors.toList());

        int profileCompleteness = calculateCompleteness(
                name,
                birthDate,
                profile.getSido(),
                profile.getIncomeLevel(),
                profile.getEmploymentStatus(),
                profile.getHouseholdType(),
                interestFields,
                priorityItems
        );

        return ProfileResponse.builder()
                .id(userId)
                .email(email)
                .name(name)
                .birthDate(birthDate)
                .sido(profile.getSido())
                .sgg(profile.getSgg())
                .regionCode(profile.getRegionCode())
                .incomeLevel(profile.getIncomeLevel())
                .ageBand(profile.getAgeBand())
                .householdType(profile.getHouseholdType())
                .employmentStatus(profile.getEmploymentStatus())
                .houseTenureCode(profile.getHouseTenureCode())
                .housingTypeCode(profile.getHousingTypeCode())
                .basicLivingRecipientTypeCode(profile.getBasicLivingRecipientTypeCode())
                .disabilityGradeCode(profile.getDisabilityGradeCode())
                .notificationYn(profile.isNotificationYn())
                .notificationEmailYn(profile.isNotificationEmailYn())
                .notificationInAppYn(profile.isNotificationInAppYn())
                .notificationWebPushYn(profile.isNotificationWebPushYn())
                .notificationPeriod(profile.getNotificationPeriod() != null ? profile.getNotificationPeriod().name() : User.NotificationPeriod.NONE.name())
                .notificationMinScore(profile.getNotificationMinScore())
                .notificationConsentAt(profile.getNotificationConsentAt())
                .displayCount(profile.getDisplayCount())
                .profileCompleteness(profileCompleteness)
                .hasPhone(profile.isHasPhone())
                .optionalProfileConsentAgreed(optionalProfileConsentAgreed)
                .sensitiveInfoConsentAgreed(sensitiveInfoConsentAgreed)
                .interestFields(interestFields)
                .targetTypes(targetTypes)
                .priorities(priorityItems)
                .build();
    }

    private static int calculateCompleteness(String name,
                                             LocalDate birthDate,
                                             String sido,
                                             Number incomeLevel,
                                             String employmentStatus,
                                             String householdType,
                                             List<String> interestFields,
                                             List<PriorityItem> priorities) {
        int score = 0;
        if (hasText(name)) score += 20;
        if (birthDate != null) score += 20;
        if (hasText(sido)) score += 10;
        if (incomeLevel != null) score += 10;
        if (hasText(employmentStatus)) score += 10;
        if (hasText(householdType)) score += 10;
        boolean hasPreference = (interestFields != null && !interestFields.isEmpty())
                || (priorities != null && !priorities.isEmpty());
        if (hasPreference) score += 20;
        return Math.min(score, 100);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
