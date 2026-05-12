package com.example.welfare.integration;

import com.example.welfare.chat.service.ChatSemanticSearchService;
import com.example.welfare.chat.service.PolicyChunkEmbeddingService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class ChatSemanticVectorIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-CHAT-VEC-";
    private static final String RARE_TOKEN = "zephirmoonstone";

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private PolicyChunkEmbeddingService policyChunkEmbeddingService;

    @Autowired
    private ChatSemanticSearchService chatSemanticSearchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        List<Long> serviceIds = welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .map(WelfareService::getId)
                .toList();
        if (serviceIds.isEmpty()) {
            return;
        }
        welfareServiceRepository.deleteAllByIdInBatch(serviceIds);
        welfareServiceRepository.flush();
    }

    @Test
    @DisplayName("pgvector 임베딩 백필 후 질문 임베딩으로 유사 정책 chunk를 검색할 수 있다")
    void semanticSearchReturnsVectorMatches() {
        WelfareService expected = saveService(
                "rent",
                "청년 주거 안정 지원 " + RARE_TOKEN,
                "청년의 월세 부담을 낮추고 주거비를 완화합니다. " + RARE_TOKEN
        );
        saveService(
                "job",
                "청년 취업 역량 강화",
                "직무 교육과 취업 역량 향상을 지원합니다."
        );

        policyChunkEmbeddingService.refreshEmbeddingsForSearchablePolicies();

        Integer embeddedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM policy_chunks
                WHERE embedding IS NOT NULL
                """, Integer.class);
        assertThat(embeddedCount).isNotNull();
        assertThat(embeddedCount).isGreaterThan(0);

        List<WelfareService> candidates = chatSemanticSearchService.findCandidates(
                "월세 주거비 부담 " + RARE_TOKEN,
                null,
                List.of(),
                100
        );

        assertThat(candidates).isNotEmpty();
        assertThat(candidates.stream()
                .map(WelfareService::getId)
                .toList()).contains(expected.getId());
    }

    private WelfareService saveService(String label, String title, String description) {
        return welfareServiceRepository.saveAndFlush(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + label + "-" + UUID.randomUUID().toString().substring(0, 8))
                .title(title)
                .description(description)
                .supportContent(description)
                .keyword(description)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .searchYouthRelevant(true)
                .apiViewCount(0L)
                .viewCount(0)
                .build());
    }
}
