package com.example.welfare.integration;

import com.example.welfare.collect.service.CollectItemSaver;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class CollectItemEmbeddingRefreshIntegrationTest {

    private static final String TEST_SOURCE_ID = "IT-COLLECT-EMBED-";

    @Autowired
    private CollectItemSaver collectItemSaver;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("수집 저장 후 커밋이 끝나면 policy chunk 와 embedding 이 자동으로 동기화된다")
    void collectSaveRefreshesEmbeddingsAfterCommit() {
        String sourceId = TEST_SOURCE_ID + UUID.randomUUID().toString().substring(0, 8);
        String initialSummary = "청년 월세 부담을 낮춰줍니다.";
        String refreshedSummary = "청년 주거비와 월세 체납 부담을 완화합니다.";

        collectItemSaver.save(saveCommand(sourceId, "청년 월세 지원", initialSummary));

        WelfareService saved = welfareServiceRepository.findBySourceTypeAndSourceId(WelfareService.SourceType.YOUTH, sourceId)
                .orElseThrow();

        Integer embeddedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM policy_chunks
                WHERE service_id = ?
                  AND embedding IS NOT NULL
                """, Integer.class, saved.getId());

        assertThat(embeddedCount).isNotNull();
        assertThat(embeddedCount).isGreaterThan(0);
        List<Map<String, Object>> initialRows = jdbcTemplate.queryForList("""
                SELECT chunk_text, embedding_text_hash
                FROM policy_chunks
                WHERE service_id = ?
                  AND embedding IS NOT NULL
                ORDER BY chunk_order ASC, id ASC
                """, saved.getId());
        assertThat(initialRows).isNotEmpty();
        assertThat(initialRows.stream()
                .map(row -> (String) row.get("chunk_text"))
                .toList()).contains(initialSummary);
        Set<String> initialHashes = initialRows.stream()
                .map(row -> (String) row.get("embedding_text_hash"))
                .collect(java.util.stream.Collectors.toSet());

        collectItemSaver.save(saveCommand(sourceId, "청년 월세 지원", refreshedSummary));

        List<Map<String, Object>> refreshedRows = jdbcTemplate.queryForList("""
                SELECT chunk_text, embedding_text_hash
                FROM policy_chunks
                WHERE service_id = ?
                  AND embedding IS NOT NULL
                ORDER BY chunk_order ASC, id ASC
                """, saved.getId());

        assertThat(refreshedRows).isNotEmpty();
        assertThat(refreshedRows.stream()
                .map(row -> (String) row.get("chunk_text"))
                .toList()).contains(refreshedSummary);
        assertThat(refreshedRows.stream()
                .map(row -> (String) row.get("embedding_text_hash")))
                .anyMatch(hash -> hash != null && !initialHashes.contains(hash));
    }

    private CollectItemSaver.SaveCommand saveCommand(String sourceId, String title, String summary) {
        WelfareService incoming = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(sourceId)
                .title(title)
                .description(summary)
                .supportContent(summary)
                .applyMethodName("온라인 신청")
                .keyword(summary)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .searchYouthRelevant(true)
                .apiViewCount(0L)
                .viewCount(0)
                .build();

        return CollectItemSaver.SaveCommand.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(sourceId)
                .incoming(incoming)
                .regions(service -> List.of())
                .tags(service -> List.of())
                .build();
    }

    private void cleanup() {
        List<Long> serviceIds = welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_ID))
                .map(WelfareService::getId)
                .toList();
        if (serviceIds.isEmpty()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM policy_chunks WHERE service_id IN (%s)".formatted(joinIds(serviceIds)));
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
