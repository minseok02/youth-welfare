package com.example.welfare.policy.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WelfareServiceSearchRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @Test
    @DisplayName("기본 첫 페이지 관련도 검색은 union 기반 cold-miss fast path를 사용한다")
    void searchUsesUnionFastPathForDefaultFirstPageRelevanceSearch() {
        WelfareServiceSearchRepositoryImpl repository = newRepository();
        stubEmptySearchRows();

        repository.search(defaultCondition(), PageRequest.of(0, 20));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(namedParameterJdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql)
                .contains("set_config('pg_trgm.similarity_threshold', :trigramThresholdText, true)")
                .contains("matched_ids AS MATERIALIZED")
                .contains("UNION")
                .contains("lower(ws.title) % :normalizedKeyword")
                .contains("lower(ws.keyword) % :normalizedKeyword")
                .contains("COUNT(*) OVER() AS total_count");
        assertThat(paramsCaptor.getValue().getValue("limit")).isEqualTo(100);
        assertThat(paramsCaptor.getValue().getValue("trigramThresholdText")).isEqualTo("0.2");
    }

    @Test
    @DisplayName("필터 검색은 기존 통합 검색 쿼리를 유지한다")
    void searchFallsBackToGeneralQueryWhenFilterIsPresent() {
        WelfareServiceSearchRepositoryImpl repository = newRepository();
        stubEmptySearchRows();

        repository.search(new PolicySearchReadCondition(
                "청년",
                null,
                "ACTIVE_ONLY",
                "주거",
                null,
                null,
                null,
                null,
                "RELEVANCE",
                null,
                null,
                null,
                null,
                null
        ), PageRequest.of(0, 20));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .doesNotContain("matched_ids AS MATERIALIZED")
                .contains("FROM welfare_services ws")
                .contains(":category IS NULL OR ws.unified_category = :category");
    }

    private WelfareServiceSearchRepositoryImpl newRepository() {
        return new WelfareServiceSearchRepositoryImpl(namedParameterJdbcTemplate, welfareServiceRepository);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubEmptySearchRows() {
        doReturn(List.of())
                .when(namedParameterJdbcTemplate)
                .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    private PolicySearchReadCondition defaultCondition() {
        return new PolicySearchReadCondition(
                "청년",
                null,
                "ACTIVE_ONLY",
                null,
                null,
                null,
                null,
                null,
                "RELEVANCE",
                null,
                null,
                null,
                null,
                null
        );
    }
}
