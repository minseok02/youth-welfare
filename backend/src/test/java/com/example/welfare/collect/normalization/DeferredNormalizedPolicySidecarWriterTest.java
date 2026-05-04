package com.example.welfare.collect.normalization;

import com.example.welfare.collect.repository.DeferredNormalizedPolicySidecarCommandRepository;
import com.example.welfare.collect.repository.DeferredNormalizedPolicySidecarReadRepository;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeferredNormalizedPolicySidecarWriterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock
    private DeferredNormalizedPolicySidecarReadRepository deferredNormalizedPolicySidecarReadRepository;
    @Mock
    private DeferredNormalizedPolicySidecarCommandRepository deferredNormalizedPolicySidecarCommandRepository;

    private DeferredNormalizedPolicySidecarWriter writer;

    @BeforeEach
    void setUp() {
        writer = new DeferredNormalizedPolicySidecarWriter(
                jdbcTemplate,
                namedParameterJdbcTemplate,
                deferredNormalizedPolicySidecarReadRepository,
                deferredNormalizedPolicySidecarCommandRepository,
                new NormalizedFactMergeSupport()
        );
        lenient().when(deferredNormalizedPolicySidecarReadRepository.findExistingFacts(anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void upsert_skipsWhenSidecarTablesAreMissing() {
        WelfareService service = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y001")
                        .title("청년 역량 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .summaryLabels(Map.of("YOUTH_MAJOR", "일자리"))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(List.of(
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("YOUTH_MID")
                                .termLabel("취업")
                                .sourceField("category_sub")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .sortOrder(0)
                                .build(),
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("YOUTH_MID_RAW_ALIAS")
                                .termLabel("온·오프라인교육")
                                .sourceField("category_sub")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .sortOrder(1)
                        .build()
                ))
                .facts(List.of())
                .build();

        given(deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady()).willReturn(false);

        assertThatCode(() -> writer.upsert(service, aggregate))
                .doesNotThrowAnyException();
        verify(namedParameterJdbcTemplate, never()).update(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
    }

    @Test
    void upsert_rejectsYouthMidRawAliasWhenSummaryYouthMidIsPresent() {
        WelfareService service = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y002")
                        .title("청년 역량 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .summaryLabels(Map.of(
                                "YOUTH_MAJOR", "일자리",
                                "YOUTH_MID", "취업"
                        ))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(List.of(
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("YOUTH_MID")
                                .termLabel("취업")
                                .sourceField("category_sub")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .sortOrder(0)
                                .build(),
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("YOUTH_MID_RAW_ALIAS")
                                .termLabel("온·오프라인교육")
                                .sourceField("category_sub")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .sortOrder(1)
                                .build()
                ))
                .facts(List.of())
                .build();

        assertThatThrownBy(() -> writer.upsert(service, aggregate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YOUTH_MID_RAW_ALIAS");
    }

    @Test
    void upsert_persistsSummaryTermsAndFactsWhenSidecarTablesExist() {
        WelfareService service = WelfareService.builder()
                .id(10L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y010")
                        .title("청년 주거 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory("주거")
                        .summaryLabels(Map.of(
                                "YOUTH_MAJOR", "주거",
                                "YOUTH_MID", "전월세 및 주거급여 지원"
                        ))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(List.of(
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("YOUTH_MID")
                                .codeSetKey("YOUTH_MID")
                                .termLabel("전월세 및 주거급여 지원")
                                .sourceField("category_sub")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .sortOrder(0)
                                .build()
                ))
                .facts(List.of(
                        NormalizedPolicyAggregate.Fact.builder()
                                .factGroup("AGE")
                                .factCode("YOUTH_AGE")
                                .factMergeKey("YOUTH_AGE_ELIGIBILITY")
                                .factLabel("지원 연령")
                                .operator(NormalizedPolicyAggregate.Operator.RANGE)
                                .valueType(NormalizedPolicyAggregate.ValueType.INTEGER)
                                .rangeMinInt(19)
                                .rangeMaxInt(34)
                                .sourceField("sprtTrgtMinAge/sprtTrgtMaxAge")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .confidence(BigDecimal.ONE)
                                .build()
                ))
                .build();

        given(deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady()).willReturn(true);
        assertThatCode(() -> writer.upsert(service, aggregate))
                .doesNotThrowAnyException();

        verify(deferredNormalizedPolicySidecarCommandRepository).upsertTaxonomySummary(service, aggregate);
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("DELETE FROM service_taxonomy_terms"), any(Object[].class));
        verify(namedParameterJdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_taxonomy_terms"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
        verify(namedParameterJdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_facts"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
    }

    @Test
    void upsert_dualWritesCanonicalSummarySlotsWhenSlotTableExists() {
        WelfareService service = WelfareService.builder()
                .id(14L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y014")
                        .title("청년 주거 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory("주거")
                        .summaryLabels(Map.of(
                                "YOUTH_MAJOR", "주거",
                                "YOUTH_MID", "전월세 및 주거급여 지원"
                        ))
                        .provisionMethod("온라인")
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(List.of(
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("YOUTH_MID")
                                .codeSetKey("YOUTH_MID")
                                .termLabel("전월세 및 주거급여 지원")
                                .sourceField("category_sub")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .sortOrder(0)
                                .build()
                ))
                .facts(List.of())
                .build();

        given(deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady()).willReturn(true);
        given(deferredNormalizedPolicySidecarReadRepository.summarySlotTableReady()).willReturn(true);

        assertThatCode(() -> writer.upsert(service, aggregate))
                .doesNotThrowAnyException();

        verify(deferredNormalizedPolicySidecarCommandRepository).replaceTaxonomySummarySlots(service, aggregate);
    }

    @Test
    void upsert_normalizesYouthMajorSummaryVariantLabel() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y011")
                        .title("청년 생활 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory("금융·생활지원")
                        .summaryLabels(Map.of("YOUTH_MAJOR", "금융･복지･문화"))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(List.of())
                .facts(List.of())
                .build();

        given(deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady()).willReturn(true);

        assertThatCode(() -> writer.upsert(service, aggregate))
                .doesNotThrowAnyException();

        verify(deferredNormalizedPolicySidecarCommandRepository).upsertTaxonomySummary(service, aggregate);
    }

    @Test
    void upsert_collapsesDuplicateYouthMajorSummaryTokens() {
        WelfareService service = WelfareService.builder()
                .id(12L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y012")
                        .title("청년 주거 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory("주거")
                        .summaryLabels(Map.of("YOUTH_MAJOR", "주거,주거"))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(List.of())
                .facts(List.of())
                .build();

        given(deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady()).willReturn(true);

        assertThatCode(() -> writer.upsert(service, aggregate))
                .doesNotThrowAnyException();

        verify(deferredNormalizedPolicySidecarCommandRepository).upsertTaxonomySummary(service, aggregate);
    }

    @Test
    void upsert_nullsYouthMajorSummaryWhenMultipleCanonicalMajorsRemain() {
        WelfareService service = WelfareService.builder()
                .id(13L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y013")
                        .title("청년 복합 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory("기타")
                        .summaryLabels(Map.of("YOUTH_MAJOR", "일자리,교육"))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(List.of())
                .facts(List.of())
                .build();

        given(deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady()).willReturn(true);

        assertThatCode(() -> writer.upsert(service, aggregate))
                .doesNotThrowAnyException();

        verify(deferredNormalizedPolicySidecarCommandRepository).upsertTaxonomySummary(service, aggregate);
    }

    @Test
    void upsert_doesNotDeleteExistingTermsWhenDetailAggregateHasNoTaxonomyTerms() {
        WelfareService service = WelfareService.builder()
                .id(20L)
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.BOKJIRO_CENTRAL)
                        .sourceId("B001")
                        .title("청년 취업 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory("일자리")
                        .authority(NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED)
                        .confidence(BigDecimal.valueOf(0.85))
                        .build())
                .taxonomyTerms(List.of())
                .facts(List.of(
                        NormalizedPolicyAggregate.Fact.builder()
                                .factGroup("AGE")
                                .factCode("BOKJIRO_RULE_AGE")
                                .factMergeKey("BK_AGE_ELIGIBILITY")
                                .factLabel("지원 연령")
                                .operator(NormalizedPolicyAggregate.Operator.RANGE)
                                .valueType(NormalizedPolicyAggregate.ValueType.INTEGER)
                                .rangeMinInt(19)
                                .rangeMaxInt(34)
                                .sourceField("targetDetail/selectionCriteria")
                                .authority(NormalizedPolicyAggregate.Authority.RULE_DERIVED)
                                .confidence(BigDecimal.valueOf(0.9))
                                .build()
                ))
                .build();

        given(deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady()).willReturn(true);
        assertThatCode(() -> writer.upsert(service, aggregate))
                .doesNotThrowAnyException();

        verify(jdbcTemplate, never()).update(org.mockito.ArgumentMatchers.contains("DELETE FROM service_taxonomy_terms"), any(Object[].class));
        verify(deferredNormalizedPolicySidecarCommandRepository).upsertTaxonomySummary(service, aggregate);
        verify(namedParameterJdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_facts"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
    }

    @Test
    void upsert_scopesBokjiroTermDeleteByGroupAndSourceField() {
        WelfareService service = WelfareService.builder()
                .id(30L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.BOKJIRO_LOCAL)
                        .sourceId("B030")
                        .title("저소득 청년 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory("금융·생활지원")
                        .authority(NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED)
                        .confidence(BigDecimal.valueOf(0.8))
                        .build())
                .taxonomyTerms(List.of(
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("TARGET_GROUP")
                                .termLabel("기초생활수급자")
                                .sourceField("targetDetail/selectionCriteria")
                                .authority(NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED)
                                .sortOrder(0)
                                .build()
                ))
                .facts(List.of())
                .build();

        given(deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady()).willReturn(true);
        assertThatCode(() -> writer.upsert(service, aggregate))
                .doesNotThrowAnyException();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("term_group = ? AND source_field = ?");
        assertThat(argsCaptor.getValue()).containsExactly(
                30L,
                "TARGET_GROUP",
                "targetDetail/selectionCriteria"
        );
    }
}
