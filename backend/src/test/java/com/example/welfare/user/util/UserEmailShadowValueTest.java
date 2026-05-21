package com.example.welfare.user.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserEmailShadowValueTest {

    @Test
    @DisplayName("실사용 도메인 이메일은 shadow 값으로 저장한다")
    void shadowsNonTestDomainEmail() {
        String shadow = UserEmailShadowValue.from("Real.User@private-domain.com");

        assertThat(shadow).startsWith("shadow_");
        assertThat(shadow).doesNotContain("@private-domain.com");
        assertThat(UserEmailShadowValue.isShadowValue(shadow)).isTrue();
    }

    @Test
    @DisplayName("example.com 같은 테스트 도메인 이메일은 그대로 유지한다")
    void preservesSafeTestDomainEmail() {
        String stored = UserEmailShadowValue.from("User@example.com");

        assertThat(stored).isEqualTo("user@example.com");
        assertThat(UserEmailShadowValue.isShadowValue(stored)).isFalse();
    }
}
