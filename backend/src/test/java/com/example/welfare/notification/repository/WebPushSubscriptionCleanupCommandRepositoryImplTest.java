package com.example.welfare.notification.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WebPushSubscriptionCleanupCommandRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate webPushSubscriptionCleanupNamedParameterJdbcTemplate;

    @InjectMocks
    private WebPushSubscriptionCleanupCommandRepositoryImpl webPushSubscriptionCleanupCommandRepository;

    @Test
    @DisplayName("web push subscription cleanup repository는 id/userKey 조건 삭제를 cleanup jdbc로 위임한다")
    void deleteByIdAndUserKeyDelegates() {
        webPushSubscriptionCleanupCommandRepository.deleteByIdAndUserKey(7L, "user-key-7");

        ArgumentCaptor<SqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        then(webPushSubscriptionCleanupNamedParameterJdbcTemplate).should().update(
                eq("""
                        DELETE FROM web_push_subscriptions
                        WHERE id = :subscriptionId
                          AND user_key = :userKey
                        """),
                parameterCaptor.capture()
        );
        assertThat(parameterCaptor.getValue().getValue("subscriptionId")).isEqualTo(7L);
        assertThat(parameterCaptor.getValue().getValue("userKey")).isEqualTo("user-key-7");
    }
}
