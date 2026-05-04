package com.example.welfare.collect.repository;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeferredNormalizedPolicySidecarCommandRepositoryImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private DeferredNormalizedPolicySidecarCommandRepositoryImpl deferredNormalizedPolicySidecarCommandRepository;

    @Test
    @DisplayName("sidecar command repository는 taxonomy summary upsert를 위임한다")
    void upsertTaxonomySummaryDelegates() {
        WelfareService service = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();
        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y001")
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
                .taxonomyTerms(List.of())
                .facts(List.of())
                .build();

        deferredNormalizedPolicySidecarCommandRepository.upsertTaxonomySummary(service, aggregate);

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_taxonomies"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getValue("serviceId")).isEqualTo(1L);
        assertThat(paramsCaptor.getValue().getValue("compatUnifiedCategoryCode")).isEqualTo("HOUSING");
    }

    @Test
    @DisplayName("sidecar command repository는 youth major variant label을 canonical code/label로 정규화한다")
    void upsertTaxonomySummaryNormalizesYouthMajorVariantLabel() {
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

        deferredNormalizedPolicySidecarCommandRepository.upsertTaxonomySummary(service, aggregate);

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_taxonomies"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getValue("youthMajorCode")).isEqualTo("WELFARE_CULTURE");
        assertThat(paramsCaptor.getValue().getValue("youthMajorLabel")).isEqualTo("복지문화");
    }

    @Test
    @DisplayName("sidecar command repository는 중복 youth major token을 collapse한다")
    void upsertTaxonomySummaryCollapsesDuplicateYouthMajorTokens() {
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

        deferredNormalizedPolicySidecarCommandRepository.upsertTaxonomySummary(service, aggregate);

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_taxonomies"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getValue("youthMajorCode")).isEqualTo("HOUSING");
        assertThat(paramsCaptor.getValue().getValue("youthMajorLabel")).isEqualTo("주거");
    }

    @Test
    @DisplayName("sidecar command repository는 multiple canonical youth major가 남으면 summary를 null 처리한다")
    void upsertTaxonomySummaryNullsYouthMajorWhenMultipleCanonicalMajorsRemain() {
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

        deferredNormalizedPolicySidecarCommandRepository.upsertTaxonomySummary(service, aggregate);

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_taxonomies"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getValue("youthMajorCode")).isNull();
        assertThat(paramsCaptor.getValue().getValue("youthMajorLabel")).isNull();
    }

    @Test
    @DisplayName("sidecar command repository는 taxonomy summary가 없으면 upsert를 건너뛴다")
    void upsertTaxonomySummarySkipsWhenNull() {
        WelfareService service = WelfareService.builder().id(1L).build();
        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y001")
                        .title("청년 주거 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomyTerms(List.of())
                .facts(List.of())
                .build();

        deferredNormalizedPolicySidecarCommandRepository.upsertTaxonomySummary(service, aggregate);

        verify(namedParameterJdbcTemplate, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_taxonomies"), any(SqlParameterSource.class));
    }

    @Test
    @DisplayName("sidecar command repository는 summary slot delete와 insert를 위임한다")
    void replaceTaxonomySummarySlotsDelegates() {
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
                .taxonomyTerms(List.of())
                .facts(List.of())
                .build();

        deferredNormalizedPolicySidecarCommandRepository.replaceTaxonomySummarySlots(service, aggregate);

        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("DELETE FROM service_taxonomy_summary_slots"), any(Object[].class));

        ArgumentCaptor<SqlParameterSource> slotParamsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate, atLeastOnce())
                .update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_taxonomy_summary_slots"), slotParamsCaptor.capture());

        assertThat(slotParamsCaptor.getAllValues())
                .extracting(params -> params.getValue("slotKey"),
                        params -> params.getValue("slotCode"),
                        params -> params.getValue("slotLabel"))
                .contains(
                        org.assertj.core.groups.Tuple.tuple("YOUTH_MAJOR", "HOUSING", "주거"),
                        org.assertj.core.groups.Tuple.tuple("YOUTH_MID", "", "전월세 및 주거급여 지원"),
                        org.assertj.core.groups.Tuple.tuple("PROVISION_METHOD", "", "온라인")
                );
    }

    @Test
    @DisplayName("sidecar command repository는 taxonomy summary가 없어도 managed slot delete만 수행한다")
    void replaceTaxonomySummarySlotsDeleteOnlyWhenNull() {
        WelfareService service = WelfareService.builder().id(15L).build();
        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y015")
                        .title("청년 생활 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomyTerms(List.of())
                .facts(List.of())
                .build();

        deferredNormalizedPolicySidecarCommandRepository.replaceTaxonomySummarySlots(service, aggregate);

        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("DELETE FROM service_taxonomy_summary_slots"), any(Object[].class));
        verify(namedParameterJdbcTemplate, never())
                .update(org.mockito.ArgumentMatchers.contains("INSERT INTO service_taxonomy_summary_slots"), any(SqlParameterSource.class));
    }
}
