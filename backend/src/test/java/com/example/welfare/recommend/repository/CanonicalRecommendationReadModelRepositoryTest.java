package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CanonicalRecommendationReadModelRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private CanonicalRecommendationReadModelRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CanonicalRecommendationReadModelRepository(namedParameterJdbcTemplate);
    }

    @Test
    @DisplayName("복지로 beneficiary raw term 두 개는 projection 에서 raw 유지 + BENEFICIARY_SUPPORT bucket 1개로 dedupe 된다")
    void findByServiceIds_buildsProjectionAndDedupeBucket() {
        Map<String, Object> baseRow = new LinkedHashMap<>();
        baseRow.put("service_id", 2309L);
        baseRow.put("source_type", "BOKJIRO_CENTRAL");
        baseRow.put("unified_category", "금융·생활지원");
        baseRow.put("title", "여성청소년 생리용품 지원");
        baseRow.put("summary", "바우처 지원");
        baseRow.put("min_age", 9);
        baseRow.put("max_age", 24);
        baseRow.put("min_income", null);
        baseRow.put("max_income", null);
        baseRow.put("apply_end_date", Date.valueOf(LocalDate.of(2026, 12, 31)));
        baseRow.put("search_youth_relevant", true);

        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM welfare_services"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(baseRow));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_taxonomy_terms"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(
                        Map.of(
                                "service_id", 2309L,
                                "term_group", "TARGET_GROUP",
                                "term_label", "기초생활수급자",
                                "source_field", "targetDetail/selectionCriteria"
                        ),
                        Map.of(
                                "service_id", 2309L,
                                "term_group", "TARGET_GROUP",
                                "term_label", "차상위계층",
                                "source_field", "targetDetail/selectionCriteria"
                        ),
                        Map.of(
                                "service_id", 2309L,
                                "term_group", "INTEREST_THEME",
                                "term_label", "생활지원",
                                "source_field", "intrsThemaArray"
                        )
                ));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_facts"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(
                        Map.of("service_id", 2309L, "fact_merge_key", "BK_AGE_ELIGIBILITY")
                ));

        Map<Long, RecommendationCandidateProjection> projections = repository.findByServiceIds(List.of(2309L));

        assertThat(projections).containsOnlyKeys(2309L);
        RecommendationCandidateProjection projection = projections.get(2309L);
        assertThat(projection.serviceId()).isEqualTo(2309L);
        assertThat(projection.sourceType()).isEqualTo("BOKJIRO_CENTRAL");
        assertThat(projection.unifiedCategoryCompat()).isEqualTo("금융·생활지원");
        assertThat(projection.title()).isEqualTo("여성청소년 생리용품 지원");
        assertThat(projection.applyEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(projection.interestThemes()).containsExactly("생활지원");
        assertThat(projection.targetGroupsRaw()).containsExactlyInAnyOrder("기초생활수급자", "차상위계층");
        assertThat(projection.beneficiaryTerms()).containsExactlyInAnyOrder("기초생활수급자", "차상위계층");
        assertThat(projection.targetGroupBuckets()).containsExactly(CanonicalRecommendationReadModelRepository.BENEFICIARY_SUPPORT_BUCKET);
        assertThat(projection.factKeys()).containsExactly("BK_AGE_ELIGIBILITY");
    }

    @Test
    @DisplayName("service id 가 비면 빈 projection 을 반환하고 DB 조회를 생략한다")
    void findByServiceIds_returnsEmptyMapForEmptyInput() {
        assertThat(repository.findByServiceIds(List.of())).isEmpty();
    }
}
