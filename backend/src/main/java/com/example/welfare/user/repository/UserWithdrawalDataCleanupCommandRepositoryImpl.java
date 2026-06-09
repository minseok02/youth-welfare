package com.example.welfare.user.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserWithdrawalDataCleanupCommandRepositoryImpl implements UserWithdrawalDataCleanupCommandRepository {

    private static final String ANONYMIZE_SUPPORT_INQUIRIES_SQL = """
            UPDATE support_inquiries
               SET user_id = NULL,
                   user_key = NULL,
                   contact_email = 'withdrawn@example.invalid',
                   message = '[withdrawn user data removed]',
                   route_path = NULL,
                   updated_at = CURRENT_TIMESTAMP
             WHERE user_key = :userKey
            """;

    private static final String ANONYMIZE_POLICY_ERROR_REPORTS_SQL = """
            UPDATE policy_error_reports
               SET user_id = NULL,
                   user_key = NULL,
                   note = NULL,
                   updated_at = CURRENT_TIMESTAMP
             WHERE user_key = :userKey
            """;

    private static final String DELETE_USER_ALERTS_SQL = """
            DELETE FROM user_alerts
            WHERE user_key = :userKey
            """;

    private static final String DELETE_NOTIFICATIONS_SQL = """
            DELETE FROM notifications
            WHERE user_key = :userKey
            """;

    private static final String DELETE_RECOMMENDATION_LOGS_SQL = """
            DELETE FROM recommendation_logs
            WHERE user_key = :userKey
            """;

    private static final String DELETE_SERVICE_VIEW_LOGS_SQL = """
            DELETE FROM service_view_logs
            WHERE user_key = :userKey
            """;

    private static final String DELETE_SEARCH_LOGS_SQL = """
            DELETE FROM search_logs
            WHERE user_key = :userKey
            """;

    private static final String DELETE_RECENT_POLICY_VIEWS_SQL = """
            DELETE FROM recent_policy_views
            WHERE user_key = :userKey
            """;

    private static final String DELETE_USER_RECOMMENDATIONS_SQL = """
            DELETE FROM user_recommendations
            WHERE user_key = :userKey
            """;

    private static final String DELETE_CHAT_SESSIONS_SQL = """
            DELETE FROM chat_sessions
            WHERE user_key = :userKey
            """;

    private static final String DELETE_WEB_PUSH_SUBSCRIPTIONS_SQL = """
            DELETE FROM web_push_subscriptions
            WHERE user_key = :userKey
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final NamedParameterJdbcTemplate recommendationPersistenceCommandNamedParameterJdbcTemplate;
    private final NamedParameterJdbcTemplate chatSessionCleanupNamedParameterJdbcTemplate;
    private final NamedParameterJdbcTemplate webPushSubscriptionCleanupNamedParameterJdbcTemplate;

    public UserWithdrawalDataCleanupCommandRepositoryImpl(
            @Qualifier("primaryNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            @Qualifier("recommendationPersistenceCommandNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate recommendationPersistenceCommandNamedParameterJdbcTemplate,
            @Qualifier("chatSessionCleanupNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate chatSessionCleanupNamedParameterJdbcTemplate,
            @Qualifier("webPushSubscriptionCleanupNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate webPushSubscriptionCleanupNamedParameterJdbcTemplate
    ) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.recommendationPersistenceCommandNamedParameterJdbcTemplate =
                recommendationPersistenceCommandNamedParameterJdbcTemplate;
        this.chatSessionCleanupNamedParameterJdbcTemplate = chatSessionCleanupNamedParameterJdbcTemplate;
        this.webPushSubscriptionCleanupNamedParameterJdbcTemplate =
                webPushSubscriptionCleanupNamedParameterJdbcTemplate;
    }

    @Override
    @Transactional
    public void cleanupByUserKey(String userKey) {
        MapSqlParameterSource params = new MapSqlParameterSource("userKey", userKey);

        namedParameterJdbcTemplate.update(ANONYMIZE_SUPPORT_INQUIRIES_SQL, params);
        namedParameterJdbcTemplate.update(ANONYMIZE_POLICY_ERROR_REPORTS_SQL, params);
        namedParameterJdbcTemplate.update(DELETE_USER_ALERTS_SQL, params);
        namedParameterJdbcTemplate.update(DELETE_NOTIFICATIONS_SQL, params);
        namedParameterJdbcTemplate.update(DELETE_RECOMMENDATION_LOGS_SQL, params);
        namedParameterJdbcTemplate.update(DELETE_SERVICE_VIEW_LOGS_SQL, params);
        namedParameterJdbcTemplate.update(DELETE_SEARCH_LOGS_SQL, params);

        recommendationPersistenceCommandNamedParameterJdbcTemplate.update(DELETE_RECENT_POLICY_VIEWS_SQL, params);
        recommendationPersistenceCommandNamedParameterJdbcTemplate.update(DELETE_USER_RECOMMENDATIONS_SQL, params);
        chatSessionCleanupNamedParameterJdbcTemplate.update(DELETE_CHAT_SESSIONS_SQL, params);
        webPushSubscriptionCleanupNamedParameterJdbcTemplate.update(DELETE_WEB_PUSH_SUBSCRIPTIONS_SQL, params);
    }
}
