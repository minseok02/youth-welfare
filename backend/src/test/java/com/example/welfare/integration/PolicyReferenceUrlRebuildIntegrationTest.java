package com.example.welfare.integration;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.repository.RawApiPayloadRepository;
import com.example.welfare.policy.dto.PolicyReferenceUrlBackfillResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.service.PolicyReferenceUrlAdminService;
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
class PolicyReferenceUrlRebuildIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-REF-URL-";

    @Autowired
    private PolicyReferenceUrlAdminService policyReferenceUrlAdminService;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private WelfareServiceDetailRepository welfareServiceDetailRepository;

    @Autowired
    private RawApiPayloadRepository rawApiPayloadRepository;

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
    @DisplayName("missingOnly=true 이면 비어 있는 referenceUrlsJson 을 youth detail raw payload 로 채운다")
    void rebuildReferenceUrlsFillsMissingReferenceUrlsFromRawPayload() throws Exception {
        String sourceId = TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        WelfareService service = saveService(sourceId, "청년 주거 지원");
        welfareServiceDetailRepository.saveAndFlush(WelfareServiceDetail.builder()
                .service(service)
                .applyMethodDetail("기존 신청 방법")
                .build());
        rawApiPayloadRepository.saveAndFlush(RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(sourceId)
                .apiCategory(RawApiPayload.ApiCategory.DETAIL)
                .payloadJson(objectMapper.writeValueAsString(youthDetailItem(
                        sourceId,
                        "온라인 접수 https://apply.example.com",
                        "지원 안내 https://support.example.com",
                        "정책 소개",
                        "https://apply.example.com",
                        "https://guide.example.com",
                        null
                )))
                .payloadHash("hash-" + sourceId)
                .fetchedAt(LocalDateTime.now())
                .build());

        PolicyReferenceUrlBackfillResponse response = policyReferenceUrlAdminService.rebuildReferenceUrls(
                List.of(WelfareService.SourceType.YOUTH),
                0,
                true
        );

        WelfareServiceDetail savedDetail = welfareServiceDetailRepository.findByServiceId(service.getId())
                .orElseThrow();

        assertThat(response.scope()).isEqualTo("selected-detail-sources");
        assertThat(response.sourceTypes()).containsExactly(WelfareService.SourceType.YOUTH);
        assertThat(response.missingOnly()).isTrue();
        assertThat(response.scannedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isZero();
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(savedDetail.getReferenceUrlsJson()).contains("https://apply.example.com");
        assertThat(savedDetail.getReferenceUrlsJson()).contains("https://guide.example.com");
        assertThat(savedDetail.getReferenceUrlsJson()).contains("https://support.example.com");
    }

    @Test
    @DisplayName("existing referenceUrlsJson 이 있으면 missingOnly=true 에서는 유지하고 missingOnly=false 에서는 raw payload 기준으로 덮어쓴다")
    void rebuildReferenceUrlsSkipsThenOverwritesExistingReferenceUrls() throws Exception {
        String sourceId = TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        WelfareService service = saveService(sourceId, "청년 금융 지원");
        welfareServiceDetailRepository.saveAndFlush(WelfareServiceDetail.builder()
                .service(service)
                .referenceUrlsJson("[{\"url\":\"https://existing.example.com\",\"type\":\"REFERENCE\"}]")
                .build());
        rawApiPayloadRepository.saveAndFlush(RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(sourceId)
                .apiCategory(RawApiPayload.ApiCategory.DETAIL)
                .payloadJson(objectMapper.writeValueAsString(youthDetailItem(
                        sourceId,
                        "모바일 신청",
                        "생활비 지원 https://fresh-support.example.com",
                        "정책 소개",
                        "https://fresh-apply.example.com",
                        null,
                        "https://fresh-reference.example.com"
                )))
                .payloadHash("hash-overwrite-" + sourceId)
                .fetchedAt(LocalDateTime.now())
                .build());

        PolicyReferenceUrlBackfillResponse skippedResponse = policyReferenceUrlAdminService.rebuildReferenceUrls(
                List.of(WelfareService.SourceType.YOUTH),
                0,
                true
        );

        WelfareServiceDetail skippedDetail = welfareServiceDetailRepository.findByServiceId(service.getId())
                .orElseThrow();
        assertThat(skippedResponse.scannedCount()).isEqualTo(1);
        assertThat(skippedResponse.skippedCount()).isEqualTo(1);
        assertThat(skippedResponse.updatedCount()).isZero();
        assertThat(skippedDetail.getReferenceUrlsJson()).contains("https://existing.example.com");
        assertThat(skippedDetail.getReferenceUrlsJson()).doesNotContain("https://fresh-apply.example.com");

        PolicyReferenceUrlBackfillResponse overwrittenResponse = policyReferenceUrlAdminService.rebuildReferenceUrls(
                List.of(WelfareService.SourceType.YOUTH),
                0,
                false
        );

        WelfareServiceDetail overwrittenDetail = welfareServiceDetailRepository.findByServiceId(service.getId())
                .orElseThrow();
        assertThat(overwrittenResponse.scannedCount()).isEqualTo(1);
        assertThat(overwrittenResponse.skippedCount()).isZero();
        assertThat(overwrittenResponse.updatedCount()).isEqualTo(1);
        assertThat(overwrittenDetail.getReferenceUrlsJson()).doesNotContain("https://existing.example.com");
        assertThat(overwrittenDetail.getReferenceUrlsJson()).contains("https://fresh-apply.example.com");
        assertThat(overwrittenDetail.getReferenceUrlsJson()).contains("https://fresh-reference.example.com");
        assertThat(overwrittenDetail.getReferenceUrlsJson()).contains("https://fresh-support.example.com");
    }

    private WelfareService saveService(String sourceId, String title) {
        return welfareServiceRepository.saveAndFlush(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(sourceId)
                .title(title)
                .description(title + " 설명")
                .supportContent(title + " 지원 내용")
                .applyMethodName("온라인 신청")
                .keyword(title)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .searchYouthRelevant(true)
                .apiViewCount(0L)
                .viewCount(0)
                .build());
    }

    private YouthApiDto.Item youthDetailItem(String sourceId,
                                             String applyMethod,
                                             String supportContent,
                                             String description,
                                             String applyUrl,
                                             String referenceUrl1,
                                             String referenceUrl2) {
        YouthApiDto.Item item = new YouthApiDto.Item();
        ReflectionTestUtils.setField(item, "plcyNo", sourceId);
        ReflectionTestUtils.setField(item, "plcyNm", "reference url rebuild test");
        ReflectionTestUtils.setField(item, "plcyAplyMthdCn", applyMethod);
        ReflectionTestUtils.setField(item, "plcySprtCn", supportContent);
        ReflectionTestUtils.setField(item, "plcyExplnCn", description);
        ReflectionTestUtils.setField(item, "aplyUrlAddr", applyUrl);
        ReflectionTestUtils.setField(item, "refUrlAddr1", referenceUrl1);
        ReflectionTestUtils.setField(item, "refUrlAddr2", referenceUrl2);
        return item;
    }

    private void cleanup() {
        List<WelfareService> services = welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .toList();
        if (services.isEmpty()) {
            return;
        }

        List<Long> serviceIds = services.stream()
                .map(WelfareService::getId)
                .toList();
        String joinedIds = joinIds(serviceIds);

        jdbcTemplate.update("DELETE FROM policy_chunks WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM service_facts WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM service_taxonomy_terms WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM service_taxonomy_summary_slots WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM service_taxonomies WHERE service_id IN (%s)".formatted(joinedIds));
        jdbcTemplate.update("DELETE FROM welfare_service_details WHERE service_id IN (%s)".formatted(joinedIds));

        rawApiPayloadRepository.deleteAll(
                rawApiPayloadRepository.findAll().stream()
                        .filter(raw -> raw.getSourceId() != null && raw.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                        .toList()
        );
        welfareServiceRepository.deleteAllByIdInBatch(serviceIds);
        welfareServiceRepository.flush();
    }

    private String joinIds(List<Long> ids) {
        return ids.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
    }
}
