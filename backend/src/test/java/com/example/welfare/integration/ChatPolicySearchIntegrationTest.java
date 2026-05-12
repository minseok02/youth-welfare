package com.example.welfare.integration;

import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.service.ChatPolicyService;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class ChatPolicySearchIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-CHAT-SRCH-";

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private ChatPolicyService chatPolicyService;

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
    @DisplayName("챗봇 후보 검색은 PostgreSQL 검색 결과를 우선 반환한다")
    void findCandidatesReturnsKeywordMatches() {
        WelfareService topMatch = saveService(
                "match",
                "서울 청년 월세 지원",
                "청년 주거비를 지원합니다.",
                "월세,주거",
                10L,
                1
        );
        saveService(
                "fallback",
                "인기 청년 정책",
                "검색어와 무관한 인기 정책입니다.",
                "도약",
                999L,
                99
        );

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("서울 월세", 5);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0).getServiceId()).isEqualTo(topMatch.getId());
    }

    @Test
    @DisplayName("챗봇 후보 검색은 질문에 검색 토큰이 없으면 인기 정책 fallback 을 사용한다")
    void findCandidatesFallsBackWhenNoSearchableToken() {
        WelfareService fallback = saveService(
                "fallback-only",
                "청년 도약 지원",
                "인기 정책 fallback",
                "도약",
                9_000_000_000L,
                900_000
        );

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("!!! ???", 5);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates.stream()
                .map(ChatPolicyCandidate::getServiceId)
                .toList()).contains(fallback.getId());
    }

    private WelfareService saveService(String label,
                                       String title,
                                       String description,
                                       String keyword,
                                       Long apiViewCount,
                                       Integer viewCount) {
        return welfareServiceRepository.saveAndFlush(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + label + "-" + UUID.randomUUID().toString().substring(0, 8))
                .title(title)
                .description(description)
                .supportContent(description)
                .keyword(keyword)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .searchYouthRelevant(true)
                .apiViewCount(apiViewCount)
                .viewCount(viewCount)
                .build());
    }
}
