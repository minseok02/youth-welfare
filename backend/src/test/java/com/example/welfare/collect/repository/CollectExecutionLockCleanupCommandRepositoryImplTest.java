package com.example.welfare.collect.repository;

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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CollectExecutionLockCleanupCommandRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate collectExecutionLockCleanupNamedParameterJdbcTemplate;

    @InjectMocks
    private CollectExecutionLockCleanupCommandRepositoryImpl collectExecutionLockCleanupCommandRepository;

    @Test
    @DisplayName("collect execution lock cleanup repository는 lock_name/owner_token 조건 release delete를 cleanup jdbc로 위임한다")
    void releaseDelegatesToCleanupJdbc() {
        given(collectExecutionLockCleanupNamedParameterJdbcTemplate.update(eq("""
                DELETE FROM collect_execution_locks
                 WHERE lock_name = :lockName
                   AND owner_token = :ownerToken
                """), org.mockito.ArgumentMatchers.any(SqlParameterSource.class))).willReturn(1);

        boolean released = collectExecutionLockCleanupCommandRepository.release("collect-global", "owner-token");

        ArgumentCaptor<SqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        then(collectExecutionLockCleanupNamedParameterJdbcTemplate).should().update(
                eq("""
                        DELETE FROM collect_execution_locks
                         WHERE lock_name = :lockName
                           AND owner_token = :ownerToken
                        """),
                parameterCaptor.capture()
        );
        assertThat(parameterCaptor.getValue().getValue("lockName")).isEqualTo("collect-global");
        assertThat(parameterCaptor.getValue().getValue("ownerToken")).isEqualTo("owner-token");
        assertThat(released).isTrue();
    }
}
