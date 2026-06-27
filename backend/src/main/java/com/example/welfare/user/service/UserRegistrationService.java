package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.repository.UserMetadataCommandRepository;
import com.example.welfare.user.repository.UserRegistrationCommandRepository;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import com.example.welfare.user.util.UserEmailShadowValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRegistrationCommandRepository userRegistrationCommandRepository;
    private final UserMetadataCommandRepository userMetadataCommandRepository;
    private final PriorityOptionReadService priorityOptionReadService;
    private final PriorityWeightPolicy priorityWeightPolicy;
    private final UserCoreSyncService userCoreSyncService;
    private final UserAccountOriginResolver userAccountOriginResolver;
    private final UserProfileStandardCodeValidator userProfileStandardCodeValidator;
    private final UserKeyLookupService userKeyLookupService;
    private final UserConsentService userConsentService;

    @Transactional
    public void register(SignupRequest request, String encodedPassword) {
        String normalizedEmail = EmailLookupKeyGenerator.normalize(request.getEmail());
        List<String> priorityCodes = normalizePriorityCodes(request.getPriorityCodes());
        String normalizedHousingTypeCode = UserProfileStandardCodeValidator.normalizeHousingTypeCode(
                request.getHouseTenureCode(),
                request.getHousingTypeCode()
        );
        userProfileStandardCodeValidator.validateProfileCodes(
                request.getHouseTenureCode(),
                normalizedHousingTypeCode,
                request.getBasicLivingRecipientTypeCode(),
                request.getDisabilityGradeCode()
        );
        validatePriorityCodes(priorityCodes);
        User user = User.builder()
                .email(UserEmailShadowValue.from(normalizedEmail))
                .accountOrigin(userAccountOriginResolver.resolve(normalizedEmail))
                .passwordHash(encodedPassword)
                .sido(request.getSido())
                .sgg(request.getSgg())
                .regionCode(RegionCodeUtil.getRegionCode(request.getSido(), request.getSgg()))
                .incomeLevel(request.getIncomeLevel())
                .employmentStatus(request.getEmploymentStatus())
                .householdType(request.getHouseholdType())
                .houseTenureCode(request.getHouseTenureCode())
                .housingTypeCode(normalizedHousingTypeCode)
                .basicLivingRecipientTypeCode(request.getBasicLivingRecipientTypeCode())
                .disabilityGradeCode(request.getDisabilityGradeCode())
                .profileCompleteness(calculateCompleteness(request, priorityCodes))
                .build();

        userRegistrationCommandRepository.save(user);
        String userKey = userKeyLookupService.findRequired(user.getId());
        userConsentService.recordSignupConsents(userKey, request);
        savePriorities(user.getId(), userKey, priorityCodes);
        userCoreSyncService.syncFromUser(
                user,
                new UserPlainPii(
                        normalizedEmail,
                        request.getName(),
                        request.getBirthDate()
                )
        );
    }

    private List<String> normalizePriorityCodes(List<String> priorityCodes) {
        if (priorityCodes == null || priorityCodes.isEmpty()) {
            return List.of();
        }
        return priorityCodes.stream()
                .map(code -> code == null ? "" : code.trim())
                .filter(code -> !code.isEmpty())
                .toList();
    }

    private void validatePriorityCodes(List<String> priorityCodes) {
        if (priorityCodes.isEmpty()) {
            return;
        }
        if (priorityCodes.size() > priorityWeightPolicy.maxRank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Set<String> uniqueCodes = new HashSet<>(priorityCodes);
        if (uniqueCodes.size() != priorityCodes.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void savePriorities(Long userId, String userKey, List<String> priorityCodes) {
        if (priorityCodes.isEmpty()) {
            return;
        }

        List<UserPriority> priorities = new ArrayList<>();
        for (int i = 0; i < priorityCodes.size(); i++) {
            PriorityOption option = priorityOptionReadService.requireByCode(priorityCodes.get(i));
            priorities.add(UserPriority.builder()
                    .userId(userId)
                    .userKey(userKey)
                    .priorityOption(option)
                    .priorityRank(i + 1)
                    .weight(priorityWeightPolicy.weightForRank(i + 1))
                    .build());
        }

        userMetadataCommandRepository.replacePriorities(userKey, priorities);
        userMetadataCommandRepository.replaceAttributes(
                userId,
                userKey,
                UserAttribute.AttrType.INTEREST_FIELD.name(),
                deriveInterestFieldsFromPriorityCodes(priorityCodes)
        );
    }

    private int calculateCompleteness(SignupRequest request, List<String> priorityCodes) {
        int score = 0;
        if (hasText(request.getName())) score += 20;
        if (request.getBirthDate() != null) score += 20;
        if (hasText(request.getSido())) score += 10;
        if (request.getIncomeLevel() != null) score += 10;
        if (hasText(request.getEmploymentStatus())) score += 10;
        if (hasText(request.getHouseholdType())) score += 10;
        if (!priorityCodes.isEmpty()) score += 20;
        return Math.min(score, 100);
    }

    private List<String> deriveInterestFieldsFromPriorityCodes(List<String> codes) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        for (String code : codes) {
            switch (code) {
                case "HOUSING" -> fields.add("주거");
                case "JOB" -> fields.add("취업");
                case "EDUCATION" -> fields.add("교육");
                case "FINANCE" -> fields.add("금융");
                case "CULTURE" -> fields.add("문화");
                case "PARTICIPATION" -> fields.add("참여");
                case "FAMILY" -> fields.add("가족돌봄");
                case "DEADLINE" -> fields.add("마감");
                default -> {
                }
            }
        }
        return new ArrayList<>(fields);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
