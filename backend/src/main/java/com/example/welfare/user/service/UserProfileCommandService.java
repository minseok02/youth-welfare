package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.service.RecommendationRefreshCacheService;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.repository.PriorityOptionReadRepository;
import com.example.welfare.user.repository.UserMetadataCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserProfileCommandService {

    private final ActiveUserReadService activeUserReadService;
    private final UserMetadataCommandRepository userMetadataCommandRepository;
    private final PriorityOptionReadRepository priorityOptionReadRepository;
    private final PriorityWeightPolicy priorityWeightPolicy;
    private final UserCoreSyncService userCoreSyncService;
    private final RecommendationRefreshCacheService recommendationRefreshCacheService;

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        User user = activeUserContext.user();
        String userKey = activeUserContext.userKey();
        recommendationRefreshCacheService.evict(userKey);

        user.updateProfile(
                request.getName() != null ? request.getName() : user.getName(),
                request.getBirthDate() != null ? request.getBirthDate() : user.getBirthDate(),
                request.getSido() != null ? request.getSido() : user.getSido(),
                request.getSgg() != null ? request.getSgg() : user.getSgg(),
                request.getRegionCode() != null ? request.getRegionCode() : user.getRegionCode(),
                request.getIncomeLevel() != null ? request.getIncomeLevel() : user.getIncomeLevel(),
                request.getHouseholdType() != null ? request.getHouseholdType() : user.getHouseholdType(),
                request.getEmploymentStatus() != null ? request.getEmploymentStatus() : user.getEmploymentStatus(),
                request.getDisplayCount() != null ? request.getDisplayCount() : user.getDisplayCount()
        );

        if (request.getNotificationYn() != null || request.getNotificationPeriod() != null || request.getNotificationMinScore() != null) {
            boolean notificationYn = request.getNotificationYn() != null ? request.getNotificationYn() : user.isNotificationYn();
            User.NotificationPeriod period = request.getNotificationPeriod() != null
                    ? parseNotificationPeriod(request.getNotificationPeriod())
                    : user.getNotificationPeriod();
            Double minScore = request.getNotificationMinScore() != null
                    ? request.getNotificationMinScore()
                    : user.getNotificationMinScore();
            LocalDateTime consentAt = notificationYn
                    ? (user.getNotificationConsentAt() != null ? user.getNotificationConsentAt() : LocalDateTime.now())
                    : null;
            user.updateNotification(notificationYn, period, minScore, consentAt);
        }

        if (request.getInterestFields() != null) {
            userMetadataCommandRepository.replaceAttributes(
                    userId,
                    userKey,
                    UserAttribute.AttrType.INTEREST_FIELD.name(),
                    request.getInterestFields()
            );
        }

        if (request.getTargetTypes() != null) {
            userMetadataCommandRepository.replaceAttributes(
                    userId,
                    userKey,
                    UserAttribute.AttrType.TARGET_TYPE.name(),
                    request.getTargetTypes()
            );
        }

        user.updateProfileCompleteness(calculateCompleteness(userKey, user, request));
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void updatePriorities(Long userId, UpdatePrioritiesRequest request) {
        String userKey = activeUserReadService.getActiveUserContext(userId).userKey();
        recommendationRefreshCacheService.evict(userKey);
        List<String> codes = request.getPriorityCodes();

        if (codes.size() > priorityWeightPolicy.maxRank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Set<String> uniqueCodes = new HashSet<>(codes);
        if (uniqueCodes.size() != codes.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        List<UserPriority> priorities = new java.util.ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            PriorityOption option = priorityOptionReadRepository.findByCode(codes.get(i))
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));

            priorities.add(UserPriority.builder()
                    .userId(userId)
                    .userKey(userKey)
                    .priorityOption(option)
                    .priorityRank(i + 1)
                    .weight(priorityWeightPolicy.weightForRank(i + 1))
                    .build());
        }
        userMetadataCommandRepository.replacePriorities(userKey, priorities);
    }

    private int calculateCompleteness(String userKey, User user, UpdateProfileRequest request) {
        int score = 0;
        if (user.getName() != null) score += 20;
        if (user.getBirthDate() != null) score += 20;
        if (user.getSido() != null) score += 10;
        if (user.getIncomeLevel() != null) score += 10;
        if (user.getEmploymentStatus() != null) score += 10;
        if (user.getHouseholdType() != null) score += 10;
        boolean hasInterestFields = request.getInterestFields() != null
                ? !request.getInterestFields().isEmpty()
                : userMetadataCommandRepository.hasAttributeValues(userKey, UserAttribute.AttrType.INTEREST_FIELD.name());
        if (hasInterestFields) score += 10;
        return Math.min(score, 100);
    }

    private User.NotificationPeriod parseNotificationPeriod(String raw) {
        String upper = raw.trim().toUpperCase();
        try {
            return User.NotificationPeriod.valueOf(upper);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
