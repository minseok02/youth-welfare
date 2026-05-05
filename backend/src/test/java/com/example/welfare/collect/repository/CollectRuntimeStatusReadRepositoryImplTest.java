package com.example.welfare.collect.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CollectRuntimeStatusReadRepositoryImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CollectRuntimeStatusReadRepositoryImpl collectRuntimeStatusReadRepository;

    @Test
    @DisplayName("runtime status read repository 는 open_until 조회를 위임한다")
    void findOpenUntilDelegates() throws Exception {
        LocalDateTime openUntil = LocalDateTime.of(2026, 5, 4, 22, 30);
        java.sql.ResultSet resultSet = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        given(resultSet.next()).willReturn(true);
        given(resultSet.getTimestamp("open_until")).willReturn(Timestamp.valueOf(openUntil));
        given(jdbcTemplate.query(any(String.class), any(ResultSetExtractor.class), eq("BOKJIRO_LOCAL")))
                .willAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    ResultSetExtractor<Optional<LocalDateTime>> extractor = invocation.getArgument(1);
                    return extractor.extractData(resultSet);
                });

        assertThat(collectRuntimeStatusReadRepository.findOpenUntil("BOKJIRO_LOCAL"))
                .contains(openUntil);
    }
}
