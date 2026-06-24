package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.entity.UserConsent;
import com.example.welfare.user.repository.UserConsentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserConsentServiceTest {

    @Mock
    private UserConsentRepository userConsentRepository;

    @Test
    @DisplayName("가입 동의 기록은 필수 동의와 요청된 선택/민감 동의만 저장한다")
    void recordSignupConsentsStoresRequestedConsentTypes() {
        UserConsentService service = new UserConsentService(userConsentRepository);
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "optionalProfileConsentAgreed", true);
        ReflectionTestUtils.setField(request, "sensitiveInfoConsentAgreed", false);
        when(userConsentRepository.findByUserKeyAndConsentType("user-key-1", UserConsent.ConsentType.PRIVACY_NOTICE))
                .thenReturn(Optional.empty());
        when(userConsentRepository.findByUserKeyAndConsentType("user-key-1", UserConsent.ConsentType.OPTIONAL_PROFILE))
                .thenReturn(Optional.empty());

        service.recordSignupConsents("user-key-1", request);

        ArgumentCaptor<UserConsent> captor = ArgumentCaptor.forClass(UserConsent.class);
        verify(userConsentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserConsent::getConsentType)
                .containsExactly(
                        UserConsent.ConsentType.PRIVACY_NOTICE,
                        UserConsent.ConsentType.OPTIONAL_PROFILE
                );
    }

    @Test
    @DisplayName("프로필 수정에 선택정보가 있고 기존 동의가 없으면 동의를 요구한다")
    void ensureProfileUpdateConsentsRejectsOptionalProfileDataWithoutConsent() {
        UserConsentService service = new UserConsentService(userConsentRepository);
        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "interestFields", List.of("주거"));
        when(userConsentRepository.existsByUserKeyAndConsentTypeAndWithdrawnAtIsNull(
                "user-key-1",
                UserConsent.ConsentType.OPTIONAL_PROFILE
        )).thenReturn(false);

        assertThatThrownBy(() -> service.ensureProfileUpdateConsents("user-key-1", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);

        verify(userConsentRepository, never()).save(any());
    }

    @Test
    @DisplayName("프로필 수정은 장애 관련 '해당 없음'도 민감정보 동의 없이는 거부한다")
    void ensureProfileUpdateConsentsRejectsDisabilityNotApplicableWithoutSensitiveConsent() {
        UserConsentService service = new UserConsentService(userConsentRepository);
        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "disabilityGradeCode", "NONE");
        when(userConsentRepository.existsByUserKeyAndConsentTypeAndWithdrawnAtIsNull(
                "user-key-1",
                UserConsent.ConsentType.SENSITIVE_INFO
        )).thenReturn(false);

        assertThatThrownBy(() -> service.ensureProfileUpdateConsents("user-key-1", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);

        verify(userConsentRepository, never()).save(any());
    }

    @Test
    @DisplayName("프로필 수정 요청에 동의 플래그가 있으면 동의를 기록하고 저장을 허용한다")
    void ensureProfileUpdateConsentsRecordsConsentFromUpdateRequest() {
        UserConsentService service = new UserConsentService(userConsentRepository);
        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "incomeLevel", (byte) 5);
        ReflectionTestUtils.setField(request, "disabilityGradeCode", "041");
        ReflectionTestUtils.setField(request, "optionalProfileConsentAgreed", true);
        ReflectionTestUtils.setField(request, "sensitiveInfoConsentAgreed", true);
        when(userConsentRepository.existsByUserKeyAndConsentTypeAndWithdrawnAtIsNull(
                "user-key-1",
                UserConsent.ConsentType.OPTIONAL_PROFILE
        )).thenReturn(false);
        when(userConsentRepository.existsByUserKeyAndConsentTypeAndWithdrawnAtIsNull(
                "user-key-1",
                UserConsent.ConsentType.SENSITIVE_INFO
        )).thenReturn(false);
        when(userConsentRepository.findByUserKeyAndConsentType("user-key-1", UserConsent.ConsentType.OPTIONAL_PROFILE))
                .thenReturn(Optional.empty());
        when(userConsentRepository.findByUserKeyAndConsentType("user-key-1", UserConsent.ConsentType.SENSITIVE_INFO))
                .thenReturn(Optional.empty());

        service.ensureProfileUpdateConsents("user-key-1", request);

        ArgumentCaptor<UserConsent> captor = ArgumentCaptor.forClass(UserConsent.class);
        verify(userConsentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserConsent::getConsentType)
                .containsExactly(
                        UserConsent.ConsentType.OPTIONAL_PROFILE,
                        UserConsent.ConsentType.SENSITIVE_INFO
                );
    }

    @Test
    @DisplayName("우선순위 저장에 기존 선택정보 동의가 없으면 동의를 요구한다")
    void ensureOptionalProfileConsentRejectsWithoutConsent() {
        UserConsentService service = new UserConsentService(userConsentRepository);
        when(userConsentRepository.existsByUserKeyAndConsentTypeAndWithdrawnAtIsNull(
                "user-key-1",
                UserConsent.ConsentType.OPTIONAL_PROFILE
        )).thenReturn(false);

        assertThatThrownBy(() -> service.ensureOptionalProfileConsent("user-key-1", false))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);

        verify(userConsentRepository, never()).save(any());
    }

    @Test
    @DisplayName("우선순위 저장 요청에 선택정보 동의가 있으면 동의를 기록한다")
    void ensureOptionalProfileConsentRecordsConsentFromPrioritiesRequest() {
        UserConsentService service = new UserConsentService(userConsentRepository);
        when(userConsentRepository.existsByUserKeyAndConsentTypeAndWithdrawnAtIsNull(
                "user-key-1",
                UserConsent.ConsentType.OPTIONAL_PROFILE
        )).thenReturn(false);
        when(userConsentRepository.findByUserKeyAndConsentType("user-key-1", UserConsent.ConsentType.OPTIONAL_PROFILE))
                .thenReturn(Optional.empty());

        service.ensureOptionalProfileConsent("user-key-1", true);

        ArgumentCaptor<UserConsent> captor = ArgumentCaptor.forClass(UserConsent.class);
        verify(userConsentRepository).save(captor.capture());
        assertThat(captor.getValue().getConsentType()).isEqualTo(UserConsent.ConsentType.OPTIONAL_PROFILE);
    }

    @Test
    @DisplayName("동의 철회는 활성 동의의 철회 시각만 기록한다")
    void withdrawMarksActiveConsentAsWithdrawn() {
        UserConsentService service = new UserConsentService(userConsentRepository);
        UserConsent consent = UserConsent.agree(
                "user-key-1",
                UserConsent.ConsentType.OPTIONAL_PROFILE,
                java.time.LocalDateTime.now().minusDays(1)
        );
        when(userConsentRepository.findByUserKeyAndConsentType("user-key-1", UserConsent.ConsentType.OPTIONAL_PROFILE))
                .thenReturn(Optional.of(consent));

        service.withdraw("user-key-1", UserConsent.ConsentType.OPTIONAL_PROFILE);

        assertThat(consent.isActive()).isFalse();
    }
}
