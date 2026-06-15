package com.example.welfare.recommend.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimilarUsersViewedPolicyReadRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Test
    @DisplayName("유사 사용자 조회 SQL은 내 최근 조회와 최신 추천 batch를 제외한다")
    void findCandidatesExcludesOwnRecentViewsAndLatestRecommendations() {
        SimilarUsersViewedPolicyReadRepositoryImpl repository =
                new SimilarUsersViewedPolicyReadRepositoryImpl(namedParameterJdbcTemplate);
        given(namedParameterJdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                any(RowMapper.class)
        )).willReturn(List.of());

        repository.findCandidates(new SimilarUsersViewedPolicyQuery(
                "user-key-1",
                25,
                "20대",
                "서울특별시",
                "11000",
                5,
                List.of("주거"),
                List.of("청년"),
                List.of("HOUSING"),
                LocalDateTime.of(2026, 6, 1, 0, 0),
                2,
                3.0,
                12
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate).query(
                sqlCaptor.capture(),
                any(SqlParameterSource.class),
                any(RowMapper.class)
        );

        assertThat(sqlCaptor.getValue())
                .contains("own_rpv.last_viewed_at >= :viewedSince")
                .contains("own_ur.recommended_at = (")
                .contains("SELECT MAX(latest_ur.recommended_at)");
    }
}
