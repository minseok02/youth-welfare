package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.entity.UserConsent;
import com.example.welfare.user.repository.UserConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserConsentService {

    private final UserConsentRepository userConsentRepository;

    @Transactional
    public void recordSignupConsents(String userKey, SignupRequest request) {
        agree(userKey, UserConsent.ConsentType.PRIVACY_NOTICE);
        if (Boolean.TRUE.equals(request.getOptionalProfileConsentAgreed())) {
            agree(userKey, UserConsent.ConsentType.OPTIONAL_PROFILE);
        }
        if (Boolean.TRUE.equals(request.getSensitiveInfoConsentAgreed())) {
            agree(userKey, UserConsent.ConsentType.SENSITIVE_INFO);
        }
    }

    @Transactional
    public void ensureProfileUpdateConsents(String userKey, UpdateProfileRequest request) {
        recordExplicitProfileUpdateConsents(userKey, request);
        ensureOptionalProfileConsent(userKey, request);
        ensureSensitiveInfoConsent(userKey, request);
    }

    @Transactional
    public void ensureOptionalProfileConsent(String userKey, Boolean optionalProfileConsentAgreed) {
        if (hasActiveConsent(userKey, UserConsent.ConsentType.OPTIONAL_PROFILE)) {
            return;
        }
        if (Boolean.TRUE.equals(optionalProfileConsentAgreed)) {
            agree(userKey, UserConsent.ConsentType.OPTIONAL_PROFILE);
            return;
        }
        throw new CustomException(ErrorCode.CONSENT_REQUIRED);
    }

    @Transactional
    public void withdrawAll(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            return;
        }
        userConsentRepository.withdrawActiveByUserKey(userKey);
    }

    @Transactional
    public void withdraw(String userKey, UserConsent.ConsentType consentType) {
        if (!StringUtils.hasText(userKey) || consentType == null) {
            return;
        }
        userConsentRepository.findByUserKeyAndConsentType(userKey, consentType)
                .filter(UserConsent::isActive)
                .ifPresent(consent -> consent.withdraw(LocalDateTime.now()));
    }

    private void ensureOptionalProfileConsent(String userKey, UpdateProfileRequest request) {
        if (!containsOptionalProfileData(request) || hasActiveConsent(userKey, UserConsent.ConsentType.OPTIONAL_PROFILE)) {
            return;
        }
        if (Boolean.TRUE.equals(request.getOptionalProfileConsentAgreed())) {
            return;
        }
        throw new CustomException(ErrorCode.CONSENT_REQUIRED);
    }

    private void ensureSensitiveInfoConsent(String userKey, UpdateProfileRequest request) {
        if (!containsSensitiveInfo(request) || hasActiveConsent(userKey, UserConsent.ConsentType.SENSITIVE_INFO)) {
            return;
        }
        if (Boolean.TRUE.equals(request.getSensitiveInfoConsentAgreed())) {
            return;
        }
        throw new CustomException(ErrorCode.CONSENT_REQUIRED);
    }

    private void recordExplicitProfileUpdateConsents(String userKey, UpdateProfileRequest request) {
        if (Boolean.TRUE.equals(request.getOptionalProfileConsentAgreed())) {
            agree(userKey, UserConsent.ConsentType.OPTIONAL_PROFILE);
        }
        if (Boolean.TRUE.equals(request.getSensitiveInfoConsentAgreed())) {
            agree(userKey, UserConsent.ConsentType.SENSITIVE_INFO);
        }
    }

    private boolean hasActiveConsent(String userKey, UserConsent.ConsentType consentType) {
        return userConsentRepository.existsByUserKeyAndConsentTypeAndWithdrawnAtIsNull(userKey, consentType);
    }

    private void agree(String userKey, UserConsent.ConsentType consentType) {
        LocalDateTime now = LocalDateTime.now();
        UserConsent consent = userConsentRepository.findByUserKeyAndConsentType(userKey, consentType)
                .orElseGet(() -> UserConsent.agree(userKey, consentType, now));
        if (consent.getId() != null) {
            consent.agreeAgain(now);
        }
        userConsentRepository.save(consent);
    }

    private boolean containsOptionalProfileData(UpdateProfileRequest request) {
        return hasText(request.getSido())
                || hasText(request.getSgg())
                || request.getIncomeLevel() != null
                || hasText(request.getHouseholdType())
                || hasText(request.getEmploymentStatus())
                || hasText(request.getHouseTenureCode())
                || hasText(request.getHousingTypeCode())
                || hasText(request.getBasicLivingRecipientTypeCode())
                || hasAnyText(request.getInterestFields())
                || hasAnyText(request.getTargetTypes());
    }

    private boolean containsSensitiveInfo(UpdateProfileRequest request) {
        return hasText(request.getDisabilityGradeCode());
    }

    private boolean hasAnyText(List<String> values) {
        return values != null && values.stream().anyMatch(this::hasText);
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
