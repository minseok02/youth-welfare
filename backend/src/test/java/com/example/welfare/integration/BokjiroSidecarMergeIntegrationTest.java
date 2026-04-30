package com.example.welfare.integration;

import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarWriter;
import com.example.welfare.collect.service.BokjiroDetailCollectService;
import com.example.welfare.collect.service.CollectItemSaver;
import com.example.welfare.collect.service.CollectResult;
import com.example.welfare.collect.service.RawApiPayloadService;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("integration")
class BokjiroSidecarMergeIntegrationTest {

    @Autowired
    private CollectItemSaver collectItemSaver;

    @Autowired
    private WelfareServiceMapper welfareServiceMapper;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private WelfareServiceDetailRepository welfareServiceDetailRepository;

    @Autowired
    private ServiceTagRepository serviceTagRepository;

    @Autowired
    private RawApiPayloadService rawApiPayloadService;

    @Autowired
    private SearchYouthRelevanceService searchYouthRelevanceService;

    @Autowired
    private NormalizedPolicySidecarWriter normalizedPolicySidecarWriter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String sourceId;

    @AfterEach
    void cleanup() {
        if (sourceId == null) {
            return;
        }
        welfareServiceRepository.findBySourceTypeAndSourceId(WelfareService.SourceType.BOKJIRO_LOCAL, sourceId)
                .ifPresent(service -> {
                    Long serviceId = service.getId();
                    jdbcTemplate.update("DELETE FROM service_facts WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM service_taxonomy_terms WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM service_taxonomies WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM welfare_service_details WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM service_tags WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM service_regions WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("""
                            DELETE FROM raw_api_payloads
                            WHERE source_type = ?
                              AND source_id = ?
                            """, WelfareService.SourceType.BOKJIRO_LOCAL.name(), sourceId);
                    jdbcTemplate.update("DELETE FROM welfare_services WHERE id = ?", serviceId);
                });
    }

    @Test
    @DisplayName("복지로 list aggregate 뒤 detail refresh 가 taxonomy term 을 유지하고 fact merge key 기준으로 sidecar 를 갱신한다")
    void refreshDetailPreservesListTermsAndOverwritesFacts() {
        sourceId = "IT-BK-SIDECAR-" + UUID.randomUUID();

        BokjiroLocalDto.Item item = bokjiroLocalItem(
                sourceId,
                "청년 문화패스",
                "만 19세 이상 34세 이하 청년에게 문화 활동비를 지원합니다.",
                "청년",
                "문화·여가",
                "청년,1인가구",
                "온라인 신청 가능",
                "https://bokjiro.go.kr/service/" + sourceId
        );

        NormalizedPolicyAggregate listAggregate = welfareServiceMapper.toNormalizedBokjiroLocal(item, null);
        collectItemSaver.saveBokjiroLocal(item, listAggregate);

        WelfareService savedBeforeRefresh = welfareServiceRepository
                .findBySourceTypeAndSourceId(WelfareService.SourceType.BOKJIRO_LOCAL, sourceId)
                .orElseThrow();

        List<Map<String, Object>> listFacts = jdbcTemplate.queryForList("""
                SELECT fact_merge_key, range_min_int, range_max_int
                FROM service_facts
                WHERE service_id = ?
                ORDER BY fact_merge_key
                """, savedBeforeRefresh.getId());

        assertThat(listFacts).hasSize(1);
        assertThat(listFacts.get(0).get("fact_merge_key")).isEqualTo("BK_AGE_ELIGIBILITY");
        assertThat(((Number) listFacts.get(0).get("range_min_int")).intValue()).isEqualTo(19);
        assertThat(((Number) listFacts.get(0).get("range_max_int")).intValue()).isEqualTo(34);

        BokjiroDetailClient detailClient = mock(BokjiroDetailClient.class);
        BokjiroDetailClient.DetailPayload detailPayload = BokjiroDetailClient.DetailPayload.builder()
                .targetDetail("만 20세 이상 39세 이하 청년")
                .supportDetail("문화 활동비 지원")
                .applyMethodDetail("온라인 신청, 2026.12.31 까지 접수")
                .selectionCriteria("연령 요건 확인")
                .contactList("복지로 고객센터")
                .supportCycle("분기별")
                .provisionType("바우처")
                .build();
        given(detailClient.fetchLocalWithStatus(sourceId))
                .willReturn(BokjiroDetailClient.FetchResult.success(detailPayload));

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CollectResult result = transactionTemplate.execute(status -> {
            WelfareService managed = welfareServiceRepository
                    .findBySourceTypeAndSourceId(WelfareService.SourceType.BOKJIRO_LOCAL, sourceId)
                    .orElseThrow();
            WelfareServiceRepository isolatedRepository = mock(WelfareServiceRepository.class);
            given(isolatedRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                    .willReturn(List.of());
            given(isolatedRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_LOCAL))
                    .willReturn(List.of(managed));

            BokjiroDetailCollectService detailCollectService = new BokjiroDetailCollectService(
                    isolatedRepository,
                    welfareServiceDetailRepository,
                    serviceTagRepository,
                    detailClient,
                    rawApiPayloadService,
                    searchYouthRelevanceService,
                    welfareServiceMapper,
                    normalizedPolicySidecarWriter
            );
            ReflectionTestUtils.setField(detailCollectService, "maxCallsPerApiPerRun", 1);
            ReflectionTestUtils.setField(detailCollectService, "requestIntervalMs", 0L);
            ReflectionTestUtils.setField(detailCollectService, "retryMaxAttempts", 1);
            ReflectionTestUtils.setField(detailCollectService, "retryBaseBackoffMs", 0L);
            ReflectionTestUtils.setField(detailCollectService, "maxConsecutiveRateLimitHits", 5);

            return detailCollectService.collectBokjiroDetailsRefreshResult(2);
        });

        assertThat(result).isNotNull();
        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.metadataJson()).contains("\"refreshExisting\":true");

        WelfareService refreshed = welfareServiceRepository
                .findBySourceTypeAndSourceId(WelfareService.SourceType.BOKJIRO_LOCAL, sourceId)
                .orElseThrow();

        assertThat(welfareServiceDetailRepository.findByServiceId(refreshed.getId()))
                .get()
                .satisfies(detail -> {
                    assertThat(detail.getTargetDetail()).isEqualTo("만 20세 이상 39세 이하 청년");
                    assertThat(detail.getSupportDetail()).isEqualTo("문화 활동비 지원");
                    assertThat(detail.getApplyMethodDetail()).isEqualTo("온라인 신청, 2026.12.31 까지 접수");
                });

        Map<String, Object> taxonomySummary = jdbcTemplate.queryForMap("""
                SELECT compat_unified_category_label,
                       youth_major_label,
                       youth_mid_label
                FROM service_taxonomies
                WHERE service_id = ?
                """, refreshed.getId());

        assertThat(taxonomySummary.get("compat_unified_category_label")).isEqualTo("문화·여가");
        assertThat(taxonomySummary.get("youth_major_label")).isNull();
        assertThat(taxonomySummary.get("youth_mid_label")).isNull();

        List<Map<String, Object>> taxonomyTerms = jdbcTemplate.queryForList("""
                SELECT term_group, term_label
                FROM service_taxonomy_terms
                WHERE service_id = ?
                ORDER BY term_group, sort_order
                """, refreshed.getId());

        Map<String, List<String>> termsByGroup = taxonomyTerms.stream()
                .collect(Collectors.groupingBy(
                        row -> (String) row.get("term_group"),
                        Collectors.mapping(row -> (String) row.get("term_label"), Collectors.toList())
                ));

        assertThat(termsByGroup.get("LIFE_STAGE")).containsExactly("청년");
        assertThat(termsByGroup.get("INTEREST_THEME")).containsExactly("문화·여가");
        assertThat(termsByGroup.get("TARGET_GROUP")).containsExactly("청년", "1인가구");

        List<Map<String, Object>> mergedFacts = jdbcTemplate.queryForList("""
                SELECT fact_merge_key,
                       source_field,
                       operator,
                       range_min_int,
                       range_max_int,
                       DATE_FORMAT(date_value, '%Y-%m-%d') AS date_value
                FROM service_facts
                WHERE service_id = ?
                ORDER BY fact_merge_key
                """, refreshed.getId());

        assertThat(mergedFacts).hasSize(2);
        assertThat(mergedFacts).anySatisfy(row -> {
            assertThat(row.get("fact_merge_key")).isEqualTo("BK_AGE_ELIGIBILITY");
            assertThat(row.get("source_field")).isEqualTo("targetDetail/selectionCriteria");
            assertThat(row.get("operator")).isEqualTo("RANGE");
            assertThat(((Number) row.get("range_min_int")).intValue()).isEqualTo(20);
            assertThat(((Number) row.get("range_max_int")).intValue()).isEqualTo(39);
        });
        assertThat(mergedFacts).anySatisfy(row -> {
            assertThat(row.get("fact_merge_key")).isEqualTo("BK_APPLY_END_DATE");
            assertThat(row.get("source_field")).isEqualTo("applyMethodDetail/supportDetail");
            assertThat(row.get("operator")).isEqualTo("EQ");
            assertThat(row.get("date_value")).isEqualTo("2026-12-31");
        });
    }

    @Test
    @DisplayName("복지로 detail phase term upsert 는 같은 TARGET_GROUP 내 list derived term 을 지우지 않고 detail derived term 만 추가한다")
    void detailPhaseTermsDoNotDeleteListDerivedTerms() {
        sourceId = "IT-BK-TERM-" + UUID.randomUUID();

        BokjiroLocalDto.Item item = bokjiroLocalItem(
                sourceId,
                "청년 문화패스",
                "만 19세 이상 34세 이하 청년에게 문화 활동비를 지원합니다.",
                "청년",
                "문화·여가",
                "청년,1인가구",
                "온라인 신청 가능",
                "https://bokjiro.go.kr/service/" + sourceId
        );

        NormalizedPolicyAggregate listAggregate = welfareServiceMapper.toNormalizedBokjiroLocal(item, null);
        collectItemSaver.saveBokjiroLocal(item, listAggregate);

        WelfareService saved = welfareServiceRepository
                .findBySourceTypeAndSourceId(WelfareService.SourceType.BOKJIRO_LOCAL, sourceId)
                .orElseThrow();

        NormalizedPolicyAggregate detailAggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.BOKJIRO_LOCAL)
                        .sourceId(sourceId)
                        .title(saved.getTitle())
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(saved.getUnifiedCategory())
                        .authority(NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED)
                        .confidence(java.math.BigDecimal.valueOf(0.7))
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

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                normalizedPolicySidecarWriter.upsert(saved, detailAggregate));

        List<Map<String, Object>> taxonomyTerms = jdbcTemplate.queryForList("""
                SELECT term_group, term_label, source_field, authority
                FROM service_taxonomy_terms
                WHERE service_id = ?
                ORDER BY term_group, source_field, sort_order
                """, saved.getId());

        assertThat(taxonomyTerms).anySatisfy(row -> {
            assertThat(row.get("term_group")).isEqualTo("TARGET_GROUP");
            assertThat(row.get("term_label")).isEqualTo("청년");
            assertThat(row.get("source_field")).isEqualTo("trgterIndvdlNmArray");
            assertThat(row.get("authority")).isEqualTo("OFFICIAL");
        });
        assertThat(taxonomyTerms).anySatisfy(row -> {
            assertThat(row.get("term_group")).isEqualTo("TARGET_GROUP");
            assertThat(row.get("term_label")).isEqualTo("1인가구");
            assertThat(row.get("source_field")).isEqualTo("trgterIndvdlNmArray");
            assertThat(row.get("authority")).isEqualTo("OFFICIAL");
        });
        assertThat(taxonomyTerms).anySatisfy(row -> {
            assertThat(row.get("term_group")).isEqualTo("TARGET_GROUP");
            assertThat(row.get("term_label")).isEqualTo("기초생활수급자");
            assertThat(row.get("source_field")).isEqualTo("targetDetail/selectionCriteria");
            assertThat(row.get("authority")).isEqualTo("SYSTEM_DERIVED");
        });
    }

    private BokjiroLocalDto.Item bokjiroLocalItem(String sourceId,
                                                  String title,
                                                  String digest,
                                                  String lifeStage,
                                                  String interestTheme,
                                                  String targetGroups,
                                                  String applyMethod,
                                                  String detailUrl) {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        ReflectionTestUtils.setField(item, "servId", sourceId);
        ReflectionTestUtils.setField(item, "servNm", title);
        ReflectionTestUtils.setField(item, "servDgst", digest);
        ReflectionTestUtils.setField(item, "lifeNmArray", lifeStage);
        ReflectionTestUtils.setField(item, "intrsThemaNmArray", interestTheme);
        ReflectionTestUtils.setField(item, "trgterIndvdlNmArray", targetGroups);
        ReflectionTestUtils.setField(item, "sprtCycNm", "분기별");
        ReflectionTestUtils.setField(item, "srvPvsnNm", "바우처");
        ReflectionTestUtils.setField(item, "aplyMtdNm", applyMethod);
        ReflectionTestUtils.setField(item, "servDtlLink", detailUrl);
        ReflectionTestUtils.setField(item, "ctpvNm", "서울특별시");
        ReflectionTestUtils.setField(item, "sggNm", "마포구");
        ReflectionTestUtils.setField(item, "inqNum", 7L);
        ReflectionTestUtils.setField(item, "enfcBgngYmd", "20260401");
        ReflectionTestUtils.setField(item, "enfcEndYmd", "20261231");
        ReflectionTestUtils.setField(item, "lastModYmd", "20260430");
        ReflectionTestUtils.setField(item, "bizChrDeptNm", "청년정책과");
        return item;
    }
}
