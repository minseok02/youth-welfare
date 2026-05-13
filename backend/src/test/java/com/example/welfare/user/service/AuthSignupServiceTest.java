package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.SignupRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthSignupServiceTest {

    @Mock
    private AuthIdentityReadService authIdentityReadService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRegistrationService userRegistrationService;
    @Mock
    private EmailVerificationService emailVerificationService;

    private AuthAdminRoleService authAdminRoleService;

    private AuthSignupService authSignupService;

    @BeforeEach
    void setUp() {
        authAdminRoleService = new AuthAdminRoleService();
        ReflectionTestUtils.setField(authAdminRoleService, "adminEmailsProperty", "admin@example.com");
        ReflectionTestUtils.invokeMethod(authAdminRoleService, "initAdminEmails");
        authSignupService = new AuthSignupService(
                authIdentityReadService,
                passwordEncoder,
                userRegistrationService,
                authAdminRoleService,
                emailVerificationService
        );
    }

    @Test
    @DisplayName("회원가입은 중복이 아니면 registration service에 저장을 위임한다")
    void signupDelegatesRegistration() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "user@example.com");
        ReflectionTestUtils.setField(request, "password", "password123!");
        ReflectionTestUtils.setField(request, "name", "홍길동");

        given(authIdentityReadService.existsByEmail("user@example.com")).willReturn(false);
        given(emailVerificationService.isVerified("user@example.com")).willReturn(true);
        given(passwordEncoder.encode("password123!")).willReturn("encoded-password");

        authSignupService.signup(request);

        then(userRegistrationService).should().register(request, "encoded-password");
        then(emailVerificationService).should().clearVerified("user@example.com");
    }

    @Test
    @DisplayName("회원가입은 이미 존재하는 이메일이어도 동일 성공으로 끝내고 저장을 시도하지 않는다")
    void signupIgnoresDuplicateEmail() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "user@example.com");
        ReflectionTestUtils.setField(request, "password", "password123!");

        given(emailVerificationService.isVerified("user@example.com")).willReturn(true);
        given(authIdentityReadService.existsByEmail("user@example.com")).willReturn(true);

        authSignupService.signup(request);

        then(userRegistrationService).should(never()).register(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        then(passwordEncoder).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원가입 저장 중 중복 제약이 발생해도 최종적으로 동일 성공으로 끝낸다")
    void signupIgnoresDuplicateRace() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "user@example.com");
        ReflectionTestUtils.setField(request, "password", "password123!");
        ReflectionTestUtils.setField(request, "name", "홍길동");

        given(emailVerificationService.isVerified("user@example.com")).willReturn(true);
        given(authIdentityReadService.existsByEmail("user@example.com")).willReturn(false, true);
        given(passwordEncoder.encode("password123!")).willReturn("encoded-password");
        org.mockito.BDDMockito.willThrow(new DataIntegrityViolationException("duplicate"))
                .given(userRegistrationService)
                .register(request, "encoded-password");

        authSignupService.signup(request);

        then(userRegistrationService).should().register(request, "encoded-password");
    }

    @Test
    @DisplayName("이메일 인증이 완료되지 않았으면 회원가입을 거절한다")
    void signupRejectsWhenEmailNotVerified() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "user@example.com");
        ReflectionTestUtils.setField(request, "password", "password123!");

        given(emailVerificationService.isVerified("user@example.com")).willReturn(false);

        assertThatThrownBy(() -> authSignupService.signup(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
    }

    @Test
    @DisplayName("관리자 예약 이메일은 공개 회원가입을 허용하지 않는다")
    void signupRejectsReservedAdminEmail() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "admin@example.com");
        ReflectionTestUtils.setField(request, "password", "password123!");

        assertThatThrownBy(() -> authSignupService.signup(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADMIN_EMAIL_SIGNUP_FORBIDDEN);
    }
}
