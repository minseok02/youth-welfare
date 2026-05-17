package com.example.welfare.user.service;

import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountOriginResolverTest {

    private final UserAccountOriginResolver resolver = new UserAccountOriginResolver();

    @Test
    @DisplayName("가입 이메일 도메인으로 account origin을 분류한다")
    void resolveByEmailDomain() {
        assertThat(resolver.resolve("alpha@example.com")).isEqualTo(User.AccountOrigin.EXAMPLE_SMOKE);
        assertThat(resolver.resolve("alpha@smoke.local")).isEqualTo(User.AccountOrigin.BOUNDED_LOCAL);
        assertThat(resolver.resolve("alpha@demo.test")).isEqualTo(User.AccountOrigin.BOUNDED_LOCAL);
        assertThat(resolver.resolve("alpha@cohortseed.app")).isEqualTo(User.AccountOrigin.LOCAL_REAL_NON_EXAMPLE_SEED);
        assertThat(resolver.resolve("alpha@real-user.kr")).isEqualTo(User.AccountOrigin.REAL_USER);
    }
}
