package com.example.welfare.chat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChatSessionCleanupCommandRepositoryImpl implements ChatSessionCleanupCommandRepository {

    private static final String DELETE_BY_USER_KEY_SQL = """
            DELETE FROM chat_sessions
            WHERE user_key = :userKey
            """;

    private static final String DELETE_BY_ID_AND_USER_KEY_SQL = """
            DELETE FROM chat_sessions
            WHERE id = :sessionId
              AND user_key = :userKey
            """;

    private final NamedParameterJdbcTemplate chatSessionCleanupNamedParameterJdbcTemplate;

    public ChatSessionCleanupCommandRepositoryImpl(
            @Qualifier("chatSessionCleanupNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate chatSessionCleanupNamedParameterJdbcTemplate
    ) {
        this.chatSessionCleanupNamedParameterJdbcTemplate = chatSessionCleanupNamedParameterJdbcTemplate;
    }

    @Override
    public void deleteByUserKey(String userKey) {
        chatSessionCleanupNamedParameterJdbcTemplate.update(
                DELETE_BY_USER_KEY_SQL,
                new MapSqlParameterSource().addValue("userKey", userKey)
        );
    }

    @Override
    public void deleteByIdAndUserKey(Long sessionId, String userKey) {
        chatSessionCleanupNamedParameterJdbcTemplate.update(
                DELETE_BY_ID_AND_USER_KEY_SQL,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userKey", userKey)
        );
    }
}
