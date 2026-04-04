package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.AesEncryptUtil;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;
    private final PriorityOptionRepository priorityOptionRepository;
    private final AesEncryptUtil aesEncryptUtil;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        User user = findActiveUser(userId);
        List<UserAttribute> attributes = userAttributeRepository.findByUserId(userId);
        List<UserPriority> priorities = userPriorityRepository.findByUserIdOrderByPriorityRank(userId);
        String phone = aesEncryptUtil.decrypt(user.getPhoneEnc());
        return ProfileResponse.of(user, attributes, priorities, phone);
    }

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);

        user.updateProfile(
                request.getName(),
                request.getBirthDate(),
                request.getSido(),
                request.getSgg(),
                request.getRegionCode(),
                request.getIncomeLevel(),
                request.getHouseholdType(),
                request.getEmploymentStatus(),
                request.getDisplayCount() != null ? request.getDisplayCount() : 10
        );

        if (request.getPhone() != null) {
            user.updatePhone(aesEncryptUtil.encrypt(request.getPhone()));
        }

        // 관심분야 속성 교체
        if (request.getInterestFields() != null) {
            userAttributeRepository.deleteByUserId(userId);
            request.getInterestFields().forEach(field ->
                    userAttributeRepository.save(UserAttribute.builder()
                            .user(user)
                            .attrType(UserAttribute.AttrType.INTEREST_FIELD.name())
                            .attrValue(field)
                            .build())
            );
        }

        user.updateProfileCompleteness(calculateCompleteness(user, request));
    }

    @Transactional
    public void updatePriorities(Long userId, UpdatePrioritiesRequest request) {
        User user = findActiveUser(userId);

        userPriorityRepository.deleteByUserId(userId);

        double[] weights = {2.0, 1.6, 1.3, 1.1, 1.0};
        List<String> codes = request.getPriorityCodes();

        for (int i = 0; i < codes.size(); i++) {
            PriorityOption option = priorityOptionRepository.findByCode(codes.get(i))
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));

            userPriorityRepository.save(UserPriority.builder()
                    .user(user)
                    .priorityOption(option)
                    .priorityRank(i + 1)
                    .weight(weights[i])
                    .build());
        }
    }

    @Transactional
    public void withdraw(Long userId, String password) {
        User user = findActiveUser(userId);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        userAttributeRepository.deleteByUserId(userId);
        userPriorityRepository.deleteByUserId(userId);
        user.withdraw();
    }

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return user;
    }

    private int calculateCompleteness(User user, UpdateProfileRequest request) {
        int score = 0;
        // 필수 항목 (각 20점)
        if (user.getName() != null) score += 20;
        if (user.getBirthDate() != null) score += 20;
        // 선택 항목 (각 10점)
        if (user.getSido() != null) score += 10;
        if (user.getIncomeLevel() != null) score += 10;
        if (user.getEmploymentStatus() != null) score += 10;
        if (user.getHouseholdType() != null) score += 10;
        if (user.getPhoneEnc() != null) score += 10;
        if (request.getInterestFields() != null && !request.getInterestFields().isEmpty()) score += 10;
        return Math.min(score, 100);
    }
}
