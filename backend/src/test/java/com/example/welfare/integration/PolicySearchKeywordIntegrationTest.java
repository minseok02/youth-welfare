package com.example.welfare.integration;

import com.example.welfare.global.util.SearchKeywordSupport;
import com.example.welfare.policy.entity.SearchLog;
import com.example.welfare.policy.repository.PolicySearchKeywordReadRepository;
import com.example.welfare.policy.repository.SearchLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class PolicySearchKeywordIntegrationTest {

    private static final String TEST_KEYWORD_PREFIX = "it-search-keyword-";
    private static final String TEST_FINGERPRINT_PREFIX = "it-search-keyword-fp-";

    @Autowired
    private SearchLogRepository searchLogRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicySearchKeywordReadRepository policySearchKeywordReadRepository;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        searchLogRepository.findAll().stream()
                .filter(log -> log.getClientFingerprint() != null && log.getClientFingerprint().startsWith(TEST_FINGERPRINT_PREFIX))
                .sorted(Comparator.comparing(SearchLog::getId).reversed())
                .forEach(searchLogRepository::delete);
    }

    @Test
    @DisplayName("인기 검색어 query는 빈 검색어, 짧은 검색어, 결과 0건 검색어를 제외하고 빈도순으로 반환한다")
    void trendingFiltersAndOrdersKeywords() throws Exception {
        LocalDateTime now = isolatedSearchWindowNow();
        saveKeywordRepeated(TEST_KEYWORD_PREFIX + "월세 지원", 20, 8L, now.minusHours(6));
        saveKeywordRepeated(TEST_KEYWORD_PREFIX + "창업 지원", 15, 5L, now.minusHours(3));
        saveKeyword(TEST_KEYWORD_PREFIX + "실패 검색", 0L, now.minusMinutes(5));
        saveKeyword("x", 4L, now.minusMinutes(3));

        List<String> trending = policySearchKeywordReadRepository.findTrendingKeywords(now.minusDays(1), 2, 2);

        assertThat(trending).containsExactly(
                TEST_KEYWORD_PREFIX + "월세 지원",
                TEST_KEYWORD_PREFIX + "창업 지원"
        );
    }

    @Test
    @DisplayName("검색 자동완성 query는 prefix를 우선하고 포함 검색 후보를 함께 반환한다")
    void suggestionsPreferPrefixAndIncludeContainsMatches() {
        LocalDateTime now = isolatedSearchWindowNow();
        saveKeyword(TEST_KEYWORD_PREFIX + "월세 지원", 9L, now.minusMinutes(30));
        saveKeyword(TEST_KEYWORD_PREFIX + "월세 지원", 7L, now.minusMinutes(5));
        saveKeyword("청년 " + TEST_KEYWORD_PREFIX + "월세", 6L, now.minusMinutes(4));
        saveKeyword(TEST_KEYWORD_PREFIX + "실패 월세", 0L, now.minusMinutes(2));

        List<String> suggestions = policySearchKeywordReadRepository.findSuggestions(
                SearchKeywordSupport.normalizeText(TEST_KEYWORD_PREFIX + "월세"),
                now.minusDays(1),
                2,
                5
        );

        assertThat(suggestions).containsExactly(
                TEST_KEYWORD_PREFIX + "월세 지원",
                "청년 " + TEST_KEYWORD_PREFIX + "월세"
        );
    }

    @Test
    @DisplayName("검색 인기어와 자동완성 endpoint는 비로그인 사용자에게 공개된다")
    void publicSearchKeywordEndpointsAreAccessibleWithoutAuthentication() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        saveKeyword(TEST_KEYWORD_PREFIX + "공개 자동완성", 4L, now.minusMinutes(1));

        mockMvc.perform(get("/api/policies/search/trending")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/policies/search/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keyword": "%s공개",
                                  "limit": 5
                                }
                                """.formatted(TEST_KEYWORD_PREFIX)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value(TEST_KEYWORD_PREFIX + "공개 자동완성"));
    }

    private void saveKeyword(String keyword, long resultCount, LocalDateTime searchedAt) {
        searchLogRepository.save(SearchLog.builder()
                .clientFingerprint(TEST_FINGERPRINT_PREFIX + Math.abs((keyword + searchedAt).hashCode()))
                .keyword(keyword)
                .resultCount(resultCount)
                .pageNumber(0)
                .pageSize(20)
                .searchedAt(searchedAt)
                .build());
    }

    private void saveKeywordRepeated(String keyword, int count, long resultCount, LocalDateTime baseTime) {
        for (int i = 0; i < count; i += 1) {
            saveKeyword(keyword, resultCount, baseTime.plusSeconds(i));
        }
    }

    private LocalDateTime isolatedSearchWindowNow() {
        return LocalDateTime.now().plusYears(20);
    }
}
