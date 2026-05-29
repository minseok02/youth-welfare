package com.example.welfare.chat.repository;

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
class ChatSessionCleanupCommandRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate chatSessionCleanupNamedParameterJdbcTemplate;

    @InjectMocks
    private ChatSessionCleanupCommandRepositoryImpl chatSessionCleanupCommandRepository;

    @Test
    @DisplayName("chat session cleanup repository는 userKey 조건 bulk delete를 cleanup jdbc로 위임한다")
    void deleteByUserKeyDelegatesToCleanupJdbc() {
        chatSessionCleanupCommandRepository.deleteByUserKey("user-key-1");

        ArgumentCaptor<SqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        then(chatSessionCleanupNamedParameterJdbcTemplate).should().update(
                eq("""
                        DELETE FROM chat_sessions
                        WHERE user_key = :userKey
                        """),
                parameterCaptor.capture()
        );
        assertThat(parameterCaptor.getValue().getValue("userKey")).isEqualTo("user-key-1");
    }

    @Test
    @DisplayName("chat session cleanup repository는 id/userKey 조건 단건 delete를 cleanup jdbc로 위임한다")
    void deleteByIdAndUserKeyDelegatesToCleanupJdbc() {
        chatSessionCleanupCommandRepository.deleteByIdAndUserKey(10L, "user-key-1");

        ArgumentCaptor<SqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        then(chatSessionCleanupNamedParameterJdbcTemplate).should().update(
                eq("""
                        DELETE FROM chat_sessions
                        WHERE id = :sessionId
                          AND user_key = :userKey
                        """),
                parameterCaptor.capture()
        );
        assertThat(parameterCaptor.getValue().getValue("sessionId")).isEqualTo(10L);
        assertThat(parameterCaptor.getValue().getValue("userKey")).isEqualTo("user-key-1");
    }
}
