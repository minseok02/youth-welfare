package com.example.welfare.user.service;

import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRegistrationCommandRepository;
import com.example.welfare.user.util.UserEmailShadowValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRegistrationCommandRepository userRegistrationCommandRepository;
    @Mock
    private UserCoreSyncService userCoreSyncService;
    @Mock
    private UserAccountOriginResolver userAccountOriginResolver;
    @Mock
    private UserProfileStandardCodeValidator userProfileStandardCodeValidator;

    @InjectMocks
    private UserRegistrationService userRegistrationService;

    @Test
    @DisplayName("회원가입 저장은 user 생성 후 core sync까지 위임한다")
    void registerSavesAndSyncsUser() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "user@example.com");
        ReflectionTestUtils.setField(request, "name", "홍길동");
        ReflectionTestUtils.setField(request, "birthDate", java.time.LocalDate.of(2000, 1, 10));
        ReflectionTestUtils.setField(request, "houseTenureCode", "3");
        ReflectionTestUtils.setField(request, "housingTypeCode", "4");
        ReflectionTestUtils.setField(request, "basicLivingRecipientTypeCode", "1");
        ReflectionTestUtils.setField(request, "disabilityGradeCode", "011");
        when(userAccountOriginResolver.resolve("user@example.com")).thenReturn(User.AccountOrigin.REAL_USER);

        userRegistrationService.register(request, "encoded-password");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userProfileStandardCodeValidator).validateProfileCodes("3", "4", "1", "011");
        verify(userRegistrationCommandRepository).save(userCaptor.capture());
        verify(userCoreSyncService).syncFromUser(
                eq(userCaptor.getValue()),
                argThat(pii -> pii != null
                        && "user@example.com".equals(pii.email())
                        && "홍길동".equals(pii.name())
                        && java.time.LocalDate.of(2000, 1, 10).equals(pii.birthDate()))
        );
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(userCaptor.getValue().getName()).isNull();
        assertThat(userCaptor.getValue().getHouseTenureCode()).isEqualTo("3");
        assertThat(userCaptor.getValue().getHousingTypeCode()).isEqualTo("4");
    }

    @Test
    @DisplayName("실사용 도메인 이메일은 users 평문 대신 shadow 값으로 저장한다")
    void registerStoresShadowValueForNonTestDomainEmail() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "real.user@private-domain.com");
        when(userAccountOriginResolver.resolve("real.user@private-domain.com")).thenReturn(User.AccountOrigin.REAL_USER);

        userRegistrationService.register(request, "encoded-password");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userProfileStandardCodeValidator).validateProfileCodes(null, null, null, null);
        verify(userRegistrationCommandRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail())
                .isEqualTo(UserEmailShadowValue.from("real.user@private-domain.com"));
    }
}
