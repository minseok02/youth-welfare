package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.user.dto.response.ProfileResponse;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.RecommendationUserReadRepository;
import com.example.welfare.user.repository.UserAccountReadRepository;
import com.example.welfare.user.repository.UserAttributeReadModel;
import com.example.welfare.user.repository.UserProfileReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserReadService {

    private final UserAccountReadRepository userAccountReadRepository;
    private final UserProfileReadRepository userProfileReadRepository;
    private final RecommendationUserReadRepository recommendationUserReadRepository;
    private final AesEncryptUtil aesEncryptUtil;
    private final UserKeyLookupService userKeyLookupService;
    private final AuthIdentityReadService authIdentityReadService;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        String userKey = resolveActiveUserKey(userId);
        var aggregate = userProfileReadRepository.findProfileAggregateByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return ProfileResponse.of(
                userId,
                decryptNullable(aggregate.pii().emailEnc()),
                decryptNullable(aggregate.pii().nameEnc()),
                parseBirthDate(aggregate.pii().birthDateEnc()),
                aggregate.profile(),
                aggregate.attributes(),
                aggregate.priorities()
        );
    }

    @Transactional(readOnly = true)
    public RecommendationUserSnapshot getRecommendationSnapshot(Long userId) {
        return getRecommendationContext(userId).snapshot();
    }

    @Transactional(readOnly = true)
    public ActiveUserContext getActiveUserContext(Long userId) {
        User user = userAccountReadRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        String userKey = user.getUserKey() != null ? user.getUserKey() : userKeyLookupService.findRequired(userId);
        return new ActiveUserContext(user, userKey);
    }

    @Transactional(readOnly = true)
    public User getActiveUserByUserKey(String userKey) {
        return findOptionalActiveUserByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<User> findOptionalActiveUserByUserKey(String userKey) {
        return userAccountReadRepository.findByUserKey(userKey)
                .filter(User::isActive);
    }

    @Transactional(readOnly = true)
    public Long requireExistingUserIdByUserKey(String userKey) {
        return userKeyLookupService.findRequiredUserId(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public RecommendationReadContext getRecommendationContext(Long userId) {
        ActiveUserContext activeUserContext = getActiveUserContext(userId);
        User user = activeUserContext.user();
        String userKey = resolveActiveUserKey(activeUserContext.userKey());
        var aggregate = recommendationUserReadRepository.findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        UserProfile profile = aggregate.profile();
        List<String> interestFields = aggregate.attributes().stream()
                .filter(attr -> UserAttribute.AttrType.INTEREST_FIELD.name().equals(attr.getAttrType()))
                .map(UserAttributeReadModel::getAttrValue)
                .toList();
        List<String> targetTypes = aggregate.attributes().stream()
                .filter(attr -> UserAttribute.AttrType.TARGET_TYPE.name().equals(attr.getAttrType()))
                .map(UserAttributeReadModel::getAttrValue)
                .toList();
        List<PriorityPreference> priorities = aggregate.priorities().stream()
                .map(priority -> new PriorityPreference(priority.getPriorityRank(), priority.getCode(), priority.getWeight()))
                .toList();

        RecommendationUserSnapshot snapshot = new RecommendationUserSnapshot(
                userId,
                userKey,
                profile.getAge(),
                profile.getAgeBand(),
                profile.getSido(),
                profile.getSgg(),
                profile.getRegionCode(),
                profile.getIncomeLevel(),
                profile.getHouseholdType(),
                profile.getEmploymentStatus(),
                profile.getDisplayCount(),
                profile.getNotificationMinScore(),
                interestFields,
                targetTypes,
                priorities
        );
        return new RecommendationReadContext(user, snapshot);
    }

    private String resolveActiveUserKey(Long userId) {
        String userKey = userKeyLookupService.findRequired(userId);
        return resolveActiveUserKey(userKey);
    }

    private String resolveActiveUserKey(String userKey) {
        return authIdentityReadService.requireActiveUserKey(userKey);
    }

    private String decryptNullable(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }
        return aesEncryptUtil.decrypt(encryptedValue);
    }

    private LocalDate parseBirthDate(String encryptedBirthDate) {
        String birthDate = decryptNullable(encryptedBirthDate);
        if (!StringUtils.hasText(birthDate)) {
            return null;
        }
        return LocalDate.parse(birthDate);
    }

    public record RecommendationReadContext(
            User user,
            RecommendationUserSnapshot snapshot
    ) {
    }

    public record ActiveUserContext(
            User user,
            String userKey
    ) {
    }
}
