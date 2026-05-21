package com.example.welfare.notification.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebPushSubscriptionCleanupCommandRepositoryImpl implements WebPushSubscriptionCleanupCommandRepository {

    private static final String DELETE_SQL = """
            DELETE FROM web_push_subscriptions
            WHERE id = :subscriptionId
              AND user_key = :userKey
            """;

    private final NamedParameterJdbcTemplate webPushSubscriptionCleanupNamedParameterJdbcTemplate;

    public WebPushSubscriptionCleanupCommandRepositoryImpl(
            @Qualifier("webPushSubscriptionCleanupNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate webPushSubscriptionCleanupNamedParameterJdbcTemplate
    ) {
        this.webPushSubscriptionCleanupNamedParameterJdbcTemplate = webPushSubscriptionCleanupNamedParameterJdbcTemplate;
    }

    @Override
    public void deleteByIdAndUserKey(Long subscriptionId, String userKey) {
        webPushSubscriptionCleanupNamedParameterJdbcTemplate.update(
                DELETE_SQL,
                new MapSqlParameterSource()
                        .addValue("subscriptionId", subscriptionId)
                        .addValue("userKey", userKey)
        );
    }
}
