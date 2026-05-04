package com.example.welfare.collect.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CollectRuntimeStatusCommandRepositoryImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CollectRuntimeStatusCommandRepositoryImpl collectRuntimeStatusCommandRepository;

    @Test
    @DisplayName("runtime status command repository 는 open_until upsert 를 수행한다")
    void upsertOpenUntilDelegates() {
        LocalDateTime openUntil = LocalDateTime.of(2026, 5, 4, 22, 30);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 5, 4, 22, 0);

        collectRuntimeStatusCommandRepository.upsertOpenUntil("BOKJIRO_LOCAL", openUntil, updatedAt);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        then(jdbcTemplate).should()
                .update(org.mockito.ArgumentMatchers.contains("insert into collect_runtime_statuses"), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertThat(args[0]).isEqualTo("BOKJIRO_LOCAL");
        assertThat(args[1]).isEqualTo(Timestamp.valueOf(openUntil));
        assertThat(args[2]).isEqualTo(Timestamp.valueOf(updatedAt));
        assertThat(args[3]).isEqualTo(Timestamp.valueOf(updatedAt));
    }
}
