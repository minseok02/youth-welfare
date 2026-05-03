package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.user.dto.response.ProfileResponse;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.NotificationTargetReadModel;
import com.example.welfare.user.repository.NotificationPiiReadRepository;
import com.example.welfare.user.repository.RecommendationUserReadRepository;
import com.example.welfare.user.repository.UserAttributeReadModel;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserProfileReadRepository;
import com.example.welfare.user.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final AuthUserRepository authUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileReadRepository userProfileReadRepository;
    private final RecommendationUserReadRepository recommendationUserReadRepository;
    private final UserPiiReadWriteRepository userPiiReadWriteRepository;
    private final NotificationPiiReadRepository notificationPiiReadRepository;
    private final AesEncryptUtil aesEncryptUtil;
    private final UserKeyLookupService userKeyLookupService;

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
        User user = userRepository.findById(userId)
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
        return userRepository.findByUserKey(userKey)
                .filter(User::isActive);
    }

    @Transactional(readOnly = true)
    public Long requireExistingUserIdByUserKey(String userKey) {
        return userRepository.findIdByUserKey(userKey)
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

    @Transactional(readOnly = true)
    public List<NotificationTarget> getNotificationTargets(User.NotificationPeriod period) {
        List<NotificationTargetReadModel> rows = userProfileRepository.findNotificationTargetsByPeriod(period.name());
        List<String> userKeys = rows.stream()
                .map(NotificationTargetReadModel::getUserKey)
                .toList();
        java.util.Map<String, String> emailByUserKey = notificationPiiReadRepository.findEncryptedEmailsByUserKeys(userKeys);

        return rows.stream()
                .map(row -> toNotificationTarget(row, emailByUserKey.get(row.getUserKey())))
                .filter(target -> StringUtils.hasText(target.email()))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getNotificationEmail(Long userId) {
        String userKey = resolveActiveUserKey(userId);
        return getNotificationEmailByUserKey(userKey);
    }

    @Transactional(readOnly = true)
    public String getNotificationEmailByUserKey(String userKey) {
        resolveActiveUserKey(userKey);
        String email = decryptNullable(notificationPiiReadRepository.findEncryptedEmailByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)));
        if (!StringUtils.hasText(email)) {
            throw new CustomException(ErrorCode.NOTIFICATION_SEND_FAILED);
        }
        return email;
    }

    private String resolveActiveUserKey(Long userId) {
        String userKey = userKeyLookupService.findRequired(userId);
        return resolveActiveUserKey(userKey);
    }

    private String resolveActiveUserKey(String userKey) {
        AuthUser authUser = authUserRepository.findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!authUser.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return userKey;
    }

    private NotificationTarget toNotificationTarget(NotificationTargetReadModel row, String emailEnc) {
        String email = decryptNullable(emailEnc);
        if (!StringUtils.hasText(email)) {
            log.warn("[UserReadService] 알림 대상 이메일 누락 userId={} userKey={}", row.getUserId(), row.getUserKey());
        }
        return new NotificationTarget(
                row.getUserId(),
                row.getUserKey(),
                email,
                User.NotificationPeriod.valueOf(row.getNotificationPeriod()),
                row.getNotificationMinScore(),
                row.getDisplayCount()
        );
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
