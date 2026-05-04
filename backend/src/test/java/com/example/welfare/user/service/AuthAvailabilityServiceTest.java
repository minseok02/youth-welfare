package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthAvailabilityServiceTest {

    @Mock
    private AuthIdentityReadService authIdentityReadService;

    @InjectMocks
    private AuthAvailabilityService authAvailabilityService;

    @Test
    @DisplayName("이메일 중복확인은 auth identity read에 위임한다")
    void checkEmailAvailabilityUsesIdentityRead() {
        given(authIdentityReadService.existsByEmail(" USER@example.com ")).willReturn(true);

        EmailAvailabilityResponse response = authAvailabilityService.checkEmailAvailability(" USER@example.com ");

        assertThat(response.available()).isFalse();
    }
}
