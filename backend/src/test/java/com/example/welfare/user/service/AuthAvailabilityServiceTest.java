package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthAvailabilityServiceTest {

    @Mock
    private AuthIdentityReadService authIdentityReadService;

    @Test
    @DisplayName("이메일 확인은 identity read service 결과를 반전해서 반환한다")
    void checkEmailAvailabilityReturnsAvailability() {
        AuthAvailabilityService authAvailabilityService = new AuthAvailabilityService(authIdentityReadService);
        given(authIdentityReadService.existsByEmail(" USER@example.com ")).willReturn(false);

        EmailAvailabilityResponse response = authAvailabilityService.checkEmailAvailability(" USER@example.com ");

        assertThat(response.available()).isTrue();
    }
}
