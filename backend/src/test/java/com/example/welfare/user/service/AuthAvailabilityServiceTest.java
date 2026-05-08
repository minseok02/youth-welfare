package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuthAvailabilityServiceTest {

    private final AuthAvailabilityService authAvailabilityService = new AuthAvailabilityService();

    @Test
    @DisplayName("이메일 확인 응답은 계정 존재 여부를 노출하지 않는다")
    void checkEmailAvailabilityDoesNotRevealAccountExistence() {
        EmailAvailabilityResponse response = authAvailabilityService.checkEmailAvailability(" USER@example.com ");

        assertThat(response.available()).isTrue();
    }
}
