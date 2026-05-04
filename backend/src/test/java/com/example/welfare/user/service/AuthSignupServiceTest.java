package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.SignupRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthSignupServiceTest {

    @Mock
    private AuthIdentityReadService authIdentityReadService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRegistrationService userRegistrationService;

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
                authAdminRoleService
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
        given(passwordEncoder.encode("password123!")).willReturn("encoded-password");

        authSignupService.signup(request);

        then(userRegistrationService).should().register(request, "encoded-password");
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
