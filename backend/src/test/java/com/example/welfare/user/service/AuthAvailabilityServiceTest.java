package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAvailabilityServiceTest {

    @Test
    @DisplayName("이메일 확인은 계정 존재 여부를 노출하지 않는 일반화 응답을 반환한다")
    void checkEmailAvailabilityReturnsGenericAvailability() {
        AuthAvailabilityService authAvailabilityService = new AuthAvailabilityService();

        EmailAvailabilityResponse response = authAvailabilityService.checkEmailAvailability(" USER@example.com ");

        assertThat(response.available()).isTrue();
    }
}
