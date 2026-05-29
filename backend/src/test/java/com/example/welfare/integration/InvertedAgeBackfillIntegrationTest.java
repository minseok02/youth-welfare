package com.example.welfare.integration;

import com.example.welfare.collect.dto.InvertedAgeBackfillResponse;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.service.InvertedAgeBackfillService;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class InvertedAgeBackfillIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-AGE-BF-";

    @Autowired
    private InvertedAgeBackfillService invertedAgeBackfillService;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("YOUTH inverted age row는 stored detail raw replay로 1회 backfill에서 복구된다")
    void backfillRepairsYouthInvalidAgeRangeFromStoredDetailRaw() throws Exception {
        String sourceId = TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        WelfareService service = welfareServiceRepository.saveAndFlush(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(sourceId)
                .title("청년 주거 지원")
                .description("청년 주거 지원 설명")
                .supportContent("청년 주거 지원")
                .applyMethodName("온라인 신청")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(35)
                .maxAge(34)
                .searchYouthRelevant(true)
                .apiViewCount(0L)
                .viewCount(0)
                .build());

        YouthApiDto.Item detail = new YouthApiDto.Item();
        ReflectionTestUtils.setField(detail, "plcyNo", sourceId);
        ReflectionTestUtils.setField(detail, "plcyNm", "청년 주거 지원");
        ReflectionTestUtils.setField(detail, "plcyAplyMthdCn", "온라인 신청");
        ReflectionTestUtils.setField(detail, "plcySprtCn", "월세 지원");
        ReflectionTestUtils.setField(detail, "sprtTrgtMinAge", 35);
        ReflectionTestUtils.setField(detail, "sprtTrgtMaxAge", 39);
        ReflectionTestUtils.setField(detail, "aplyUrlAddr", "https://apply.example.com");

        jdbcTemplate.update("""
                INSERT INTO raw_api_payloads (source_type, source_id, api_category, payload_json, payload_hash, fetched_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                WelfareService.SourceType.YOUTH.name(),
                sourceId,
                RawApiPayload.ApiCategory.DETAIL.name(),
                objectMapper.writeValueAsString(detail),
                "hash-" + sourceId,
                LocalDateTime.now()
        );

        InvertedAgeBackfillResponse response = invertedAgeBackfillService.backfill(
                List.of(WelfareService.SourceType.YOUTH),
                5000
        );

        WelfareService repaired = welfareServiceRepository.findById(service.getId()).orElseThrow();
        assertThat(response.scope()).isEqualTo("selected-source-types");
        assertThat(response.scannedCount()).isGreaterThanOrEqualTo(1);
        assertThat(response.repairedCount()).isGreaterThanOrEqualTo(1);
        assertThat(repaired.getMinAge()).isEqualTo(35);
        assertThat(repaired.getMaxAge()).isEqualTo(39);

        var ageFact = jdbcTemplate.queryForMap("""
                SELECT range_min_int, range_max_int
                FROM service_facts
                WHERE service_id = ?
                  AND fact_group = ?
                ORDER BY id
                LIMIT 1
                """, repaired.getId(), "AGE");
        assertThat(((Number) ageFact.get("range_min_int")).intValue()).isEqualTo(35);
        assertThat(((Number) ageFact.get("range_max_int")).intValue()).isEqualTo(39);
    }

    private void cleanup() {
        List<WelfareService> services = welfareServiceRepository.findBySourceType(WelfareService.SourceType.YOUTH).stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .toList();
        if (services.isEmpty()) {
            return;
        }

        List<Long> serviceIds = services.stream().map(WelfareService::getId).toList();
        String joinedIds = serviceIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM policy_chunks WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM service_facts WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM service_taxonomy_terms WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM service_taxonomy_summary_slots WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM service_taxonomies WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM welfare_service_details WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM raw_api_payloads WHERE source_type = ? AND source_id LIKE ?",
                WelfareService.SourceType.YOUTH.name(), TEST_SOURCE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM welfare_services WHERE id IN (%s)".formatted(joinedIds));
    }
}
