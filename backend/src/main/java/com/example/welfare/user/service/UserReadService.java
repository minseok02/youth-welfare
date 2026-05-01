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
import com.example.welfare.user.repository.UserAttributeReadModel;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserPriorityReadModel;
import com.example.welfare.user.repository.UserPriorityRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserReadService {

    private final UserRepository userRepository;
    private final AuthUserRepository authUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserPiiReadWriteRepository userPiiReadWriteRepository;
    private final NotificationPiiReadRepository notificationPiiReadRepository;
    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;
    private final AesEncryptUtil aesEncryptUtil;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        String userKey = resolveActiveUserKey(userId);
        UserProfile profile = userProfileRepository.findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        var pii = userPiiReadWriteRepository.findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<UserAttributeReadModel> attributes = userAttributeRepository.findReadModelsByUserKey(userKey);
        List<UserPriorityReadModel> priorities = userPriorityRepository.findReadModelsByUserKey(userKey);

        return ProfileResponse.of(
                userId,
                decryptNullable(pii.emailEnc()),
                decryptNullable(pii.nameEnc()),
                parseBirthDate(pii.birthDateEnc()),
                decryptNullable(pii.phoneEnc()),
                profile,
                attributes,
                priorities
        );
    }

    @Transactional(readOnly = true)
    public RecommendationUserSnapshot getRecommendationSnapshot(Long userId) {
        String userKey = resolveActiveUserKey(userId);
        UserProfile profile = userProfileRepository.findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        List<UserAttributeReadModel> attributes = userAttributeRepository.findReadModelsByUserKey(userKey);
        List<String> interestFields = attributes.stream()
                .filter(attr -> UserAttribute.AttrType.INTEREST_FIELD.name().equals(attr.getAttrType()))
                .map(UserAttributeReadModel::getAttrValue)
                .toList();
        List<String> targetTypes = attributes.stream()
                .filter(attr -> UserAttribute.AttrType.TARGET_TYPE.name().equals(attr.getAttrType()))
                .map(UserAttributeReadModel::getAttrValue)
                .toList();
        List<PriorityPreference> priorities = userPriorityRepository.findReadModelsByUserKey(userKey).stream()
                .map(priority -> new PriorityPreference(priority.getPriorityRank(), priority.getCode(), priority.getWeight()))
                .toList();

        return new RecommendationUserSnapshot(
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
        String userKey = userRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
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
}
