package com.example.welfare.global.util;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtUtil, "notificationExpiration", 3_600_000L);
        jwtUtil.init();
    }

    @Test
    @DisplayName("access token은 iatm claim을 기록하고 읽을 수 있다")
    void accessTokenStoresIssuedAtMillis() {
        String token = jwtUtil.generateAccessToken("user-key-7", 7L);

        long issuedAtMillis = jwtUtil.getIssuedAtMillis(token);

        assertThat(issuedAtMillis).isPositive();
    }

    @Test
    @DisplayName("refresh token은 iatm claim이 없어 getIssuedAtMillis에서 실패한다")
    void refreshTokenWithoutIssuedAtMillisFails() {
        String token = jwtUtil.generateRefreshToken("user-key-7", 7L);

        assertThatThrownBy(() -> jwtUtil.getIssuedAtMillis(token))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("만료된 access token도 getIssuedAtMillisAllowExpired로 iatm을 읽을 수 있다")
    void getIssuedAtMillisAllowExpiredReadsExpiredAccessToken() throws Exception {
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 1L);
        String token = jwtUtil.generateAccessToken("user-key-7", 7L);

        Thread.sleep(10L);

        long issuedAtMillis = jwtUtil.getIssuedAtMillisAllowExpired(token);

        assertThat(issuedAtMillis).isPositive();
    }
}
