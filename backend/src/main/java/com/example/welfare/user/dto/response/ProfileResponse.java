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
    private String householdType;
    private String employmentStatus;
    private boolean notificationYn;
    private String notificationPeriod;
    private Double notificationMinScore;
    private int displayCount;
    private int profileCompleteness;
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

        return ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .birthDate(user.getBirthDate())
                .sido(user.getSido())
                .sgg(user.getSgg())
                .regionCode(user.getRegionCode())
                .incomeLevel(user.getIncomeLevel())
                .householdType(user.getHouseholdType())
                .employmentStatus(user.getEmploymentStatus())
                .notificationYn(user.isNotificationYn())
                .notificationPeriod(user.getNotificationPeriod().name())
                .notificationMinScore(user.getNotificationMinScore())
                .displayCount(user.getDisplayCount())
                .profileCompleteness(user.getProfileCompleteness())
                .interestFields(interestFields)
                .targetTypes(targetTypes)
                .priorities(priorityItems)
                .build();
    }

    public static ProfileResponse of(Long userId, String email, String name, LocalDate birthDate,
                                     UserProfile profile,
                                     List<UserAttributeReadModel> attributes,
                                     List<UserPriorityReadModel> priorities) {
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

        return ProfileResponse.builder()
                .id(userId)
                .email(email)
                .name(name)
                .birthDate(birthDate)
                .sido(profile.getSido())
                .sgg(profile.getSgg())
                .regionCode(profile.getRegionCode())
                .incomeLevel(profile.getIncomeLevel())
                .householdType(profile.getHouseholdType())
                .employmentStatus(profile.getEmploymentStatus())
                .notificationYn(profile.isNotificationYn())
                .notificationPeriod(profile.getNotificationPeriod() != null ? profile.getNotificationPeriod().name() : User.NotificationPeriod.NONE.name())
                .notificationMinScore(profile.getNotificationMinScore())
                .displayCount(profile.getDisplayCount())
                .profileCompleteness(profile.getProfileCompleteness())
                .interestFields(interestFields)
                .targetTypes(targetTypes)
                .priorities(priorityItems)
                .build();
    }
}
