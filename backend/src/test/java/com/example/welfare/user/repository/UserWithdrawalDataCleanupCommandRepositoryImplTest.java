package com.example.welfare.user.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class UserWithdrawalDataCleanupCommandRepositoryImplTest {

    @Test
    @DisplayName("withdrawal cleanup은 사용자 데이터 테이블을 datasource 권한 경계별로 정리한다")
    void cleanupByUserKeyUsesScopedDataSources() {
        NamedParameterJdbcTemplate primaryJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        NamedParameterJdbcTemplate recommendationPersistenceJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        NamedParameterJdbcTemplate chatCleanupJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        NamedParameterJdbcTemplate webPushCleanupJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        UserWithdrawalDataCleanupCommandRepositoryImpl repository =
                new UserWithdrawalDataCleanupCommandRepositoryImpl(
                        primaryJdbcTemplate,
                        recommendationPersistenceJdbcTemplate,
                        chatCleanupJdbcTemplate,
                        webPushCleanupJdbcTemplate
                );

        repository.cleanupByUserKey("user-key-1");

        then(primaryJdbcTemplate).should().update(contains("UPDATE support_inquiries"), any(MapSqlParameterSource.class));
        then(primaryJdbcTemplate).should().update(contains("UPDATE policy_error_reports"), any(MapSqlParameterSource.class));
        then(primaryJdbcTemplate).should().update(contains("DELETE FROM user_alerts"), any(MapSqlParameterSource.class));
        then(primaryJdbcTemplate).should().update(contains("DELETE FROM notifications"), any(MapSqlParameterSource.class));
        then(primaryJdbcTemplate).should().update(contains("DELETE FROM recommendation_logs"), any(MapSqlParameterSource.class));
        then(primaryJdbcTemplate).should().update(contains("DELETE FROM service_view_logs"), any(MapSqlParameterSource.class));
        then(primaryJdbcTemplate).should().update(contains("DELETE FROM search_logs"), any(MapSqlParameterSource.class));

        then(recommendationPersistenceJdbcTemplate).should()
                .update(contains("DELETE FROM recent_policy_views"), any(MapSqlParameterSource.class));
        then(recommendationPersistenceJdbcTemplate).should()
                .update(contains("DELETE FROM user_recommendations"), any(MapSqlParameterSource.class));
        then(chatCleanupJdbcTemplate).should().update(contains("DELETE FROM chat_sessions"), any(MapSqlParameterSource.class));
        then(webPushCleanupJdbcTemplate).should()
                .update(contains("DELETE FROM web_push_subscriptions"), any(MapSqlParameterSource.class));
    }
}
