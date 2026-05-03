package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.recommend.service.RecommendationRefreshCacheService;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.dto.response.ProfileResponse;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.repository.PriorityOptionRepository;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final UserRepository userRepository;
    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;
    private final PriorityOptionRepository priorityOptionRepository;
    private final PriorityWeightPolicy priorityWeightPolicy;
    private final PasswordEncoder passwordEncoder;
    private final UserRecommendationRepository userRecommendationRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final ChatSessionCleanupService chatSessionCleanupService;
    private final UserCoreSyncService userCoreSyncService;
    private final UserReadService userReadService;
    private final CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;
    private final RecommendationRefreshCacheService recommendationRefreshCacheService;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return userReadService.getProfile(userId);
    }

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> getBookmarks(Long userId) {
        findActiveUser(userId);
        List<UserRecommendation> bookmarks = userRecommendationRepository.findLatestBookmarkedByUserKey(resolveUserKey(userId));
        java.util.Map<Long, com.example.welfare.recommend.dto.RecommendationCandidateProjection> projections =
                canonicalRecommendationReadModelRepository.findByServiceIds(
                        bookmarks.stream().map(bookmark -> bookmark.getService().getId()).toList()
                );
        return bookmarks.stream()
                .map(UserRecommendation::getService)
                .map(service -> PolicySummaryResponse.from(service, true, projections.get(service.getId())))
                .toList();
    }

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);
        String userKey = resolveUserKey(userId);
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

        // 관심분야 속성 교체
        if (request.getInterestFields() != null) {
            userAttributeRepository.deleteByUserKeyAndAttrType(userKey, UserAttribute.AttrType.INTEREST_FIELD.name());
            request.getInterestFields().forEach(field ->
                    userAttributeRepository.save(UserAttribute.builder()
                            .userId(userId)
                            .userKey(userKey)
                            .attrType(UserAttribute.AttrType.INTEREST_FIELD.name())
                            .attrValue(field)
                            .build())
            );
        }

        if (request.getTargetTypes() != null) {
            userAttributeRepository.deleteByUserKeyAndAttrType(userKey, UserAttribute.AttrType.TARGET_TYPE.name());
            request.getTargetTypes().forEach(targetType ->
                    userAttributeRepository.save(UserAttribute.builder()
                            .userId(userId)
                            .userKey(userKey)
                            .attrType(UserAttribute.AttrType.TARGET_TYPE.name())
                            .attrValue(targetType)
                            .build())
            );
        }

        user.updateProfileCompleteness(calculateCompleteness(userKey, user, request));
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void updatePriorities(Long userId, UpdatePrioritiesRequest request) {
        User user = findActiveUser(userId);
        String userKey = resolveUserKey(userId);
        recommendationRefreshCacheService.evict(userKey);
        List<String> codes = request.getPriorityCodes();

        if (codes.size() > priorityWeightPolicy.maxRank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Set<String> uniqueCodes = new HashSet<>(codes);
        if (uniqueCodes.size() != codes.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        userPriorityRepository.deleteByUserKey(userKey);

        for (int i = 0; i < codes.size(); i++) {
            PriorityOption option = priorityOptionRepository.findByCode(codes.get(i))
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));

            userPriorityRepository.save(UserPriority.builder()
                    .userId(userId)
                    .userKey(userKey)
                    .priorityOption(option)
                    .priorityRank(i + 1)
                    .weight(priorityWeightPolicy.weightForRank(i + 1))
                    .build());
        }
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findActiveUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void withdraw(Long userId, String password, String accessToken) {
        User user = findActiveUser(userId);
        String userKey = user.getUserKey() != null ? user.getUserKey() : resolveUserKey(userId);
        recommendationRefreshCacheService.evict(userKey);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        userAttributeRepository.deleteByUserKey(userKey);
        userPriorityRepository.deleteByUserKey(userKey);
        chatSessionCleanupService.deleteAllByUserKey(userKey);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userKey);
        revokePresentedAccessToken(accessToken);
        user.withdraw();
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void unsubscribeNotifications(Long userId) {
        User user = findActiveUser(userId);
        user.unsubscribeNotifications();
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void unsubscribeNotificationsByUserKey(String userKey) {
        Long userId = userRepository.findIdByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        unsubscribeNotifications(userId);
    }

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return user;
    }

    private void revokePresentedAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        accessTokenRevocationService.revoke(accessToken);
    }

    private String resolveUserKey(Long userId) {
        return userRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private int calculateCompleteness(String userKey, User user, UpdateProfileRequest request) {
        int score = 0;
        // 필수 항목 (각 20점)
        if (user.getName() != null) score += 20;
        if (user.getBirthDate() != null) score += 20;
        // 선택 항목 (각 10점)
        if (user.getSido() != null) score += 10;
        if (user.getIncomeLevel() != null) score += 10;
        if (user.getEmploymentStatus() != null) score += 10;
        if (user.getHouseholdType() != null) score += 10;
        boolean hasInterestFields = request.getInterestFields() != null
                ? !request.getInterestFields().isEmpty()
                : !userAttributeRepository.findByUserKeyAndAttrType(userKey, UserAttribute.AttrType.INTEREST_FIELD.name()).isEmpty();
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
