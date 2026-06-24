package com.example.welfare.policy.service;

import com.example.welfare.policy.repository.PolicySearchKeywordReadRepository;
import com.example.welfare.policy.repository.WelfareServiceSearchRepository;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PolicySearchKeywordReadServiceTest {

    @Mock
    private PolicySearchKeywordReadRepository policySearchKeywordReadRepository;

    @Mock
    private WelfareServiceSearchRepository welfareServiceSearchRepository;

    @InjectMocks
    private PolicySearchKeywordReadService policySearchKeywordReadService;

    @Test
    @DisplayName("인기 검색어 조회는 기본/최대 limit을 정규화한다")
    void getTrendingKeywordsNormalizesLimit() {
        given(policySearchKeywordReadRepository.findTrendingKeywords(any(LocalDateTime.class), eq(2), eq(10)))
                .willReturn(List.of("월세 지원", "010-1234-5678", "도약계좌"));

        List<String> response = policySearchKeywordReadService.getTrendingKeywords(99);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(policySearchKeywordReadRepository).should()
                .findTrendingKeywords(cutoffCaptor.capture(), eq(2), eq(10));
        assertThat(cutoffCaptor.getValue()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(response).containsExactly("월세 지원", "도약계좌");
    }

    @Test
    @DisplayName("검색 자동완성은 공백 입력이면 빈 목록을 반환한다")
    void getSuggestionsReturnsEmptyListForBlankKeyword() {
        assertThat(policySearchKeywordReadService.getSuggestions("   ", 5)).isEmpty();

        then(policySearchKeywordReadRepository).should(never())
                .findSuggestions(org.mockito.ArgumentMatchers.anyString(), any(LocalDateTime.class), eq(2), eq(5));
    }

    @Test
    @DisplayName("검색 자동완성은 입력과 limit을 정규화해 조회한다")
    void getSuggestionsNormalizesKeywordAndLimit() {
        given(welfareServiceSearchRepository.searchChatCandidates(eq("월세 지원"), eq(5)))
                .willReturn(List.of(
                        WelfareService.builder().title("청년 월세 지원").build(),
                        WelfareService.builder().title("월세 지원").build()
                ));
        given(policySearchKeywordReadRepository.findSuggestions(eq("월세 지원"), any(LocalDateTime.class), eq(2), eq(8)))
                .willReturn(List.of("월세 지원", "010-1234-5678", "청년 월세 지원", "월세 긴급 지원"));

        List<String> response = policySearchKeywordReadService.getSuggestions("  월세!   지원?  ", null);

        then(policySearchKeywordReadRepository).should()
                .findSuggestions(eq("월세 지원"), any(LocalDateTime.class), eq(2), eq(8));
        then(welfareServiceSearchRepository).should().searchChatCandidates(eq("월세 지원"), eq(5));
        assertThat(response).containsExactly("월세 지원", "청년 월세 지원", "월세 긴급 지원");
    }

    @Test
    @DisplayName("검색 자동완성은 로그 후보를 우선하고 부족한 자리를 정책 title 후보로 보강한다")
    void getSuggestionsMergesPolicyCandidatesAndLogSuggestions() {
        given(welfareServiceSearchRepository.searchChatCandidates(eq("청년"), eq(5)))
                .willReturn(List.of(
                        WelfareService.builder().title("청년 월세 지원").build(),
                        WelfareService.builder().title("청년 도약계좌").build(),
                        WelfareService.builder().title("청년 창업 지원").build()
                ));
        given(policySearchKeywordReadRepository.findSuggestions(eq("청년"), any(LocalDateTime.class), eq(2), eq(5)))
                .willReturn(List.of("청년 도약계좌", "청년 취업 지원"));

        List<String> response = policySearchKeywordReadService.getSuggestions("청년", 5);

        assertThat(response).containsExactly(
                "청년 도약계좌",
                "청년 취업 지원",
                "청년 월세 지원",
                "청년 창업 지원"
        );
    }

    @Test
    @DisplayName("검색 자동완성은 더 정확히 맞는 정책 title 후보를 로그 후보보다 먼저 올린다")
    void getSuggestionsPromotesBetterMatchingPolicyTitle() {
        given(welfareServiceSearchRepository.searchChatCandidates(eq("국민취업지원제도"), eq(5)))
                .willReturn(List.of(
                        WelfareService.builder().title("국민 취업 지원 제도").build(),
                        WelfareService.builder().title("국민 취업 지원 프로그램").build()
                ));
        given(policySearchKeywordReadRepository.findSuggestions(eq("국민취업지원제도"), any(LocalDateTime.class), eq(2), eq(5)))
                .willReturn(List.of("취업 지원", "국민 지원"));

        List<String> response = policySearchKeywordReadService.getSuggestions("국민취업지원제도", 5);

        assertThat(response).containsExactly(
                "국민 취업 지원 제도",
                "취업 지원",
                "국민 지원",
                "국민 취업 지원 프로그램"
        );
    }

    @Test
    @DisplayName("검색 자동완성은 로그 후보가 limit을 채우면 정책 title 조회를 건너뛴다")
    void getSuggestionsSkipsPolicyCandidatesWhenLogSuggestionsFillLimit() {
        given(policySearchKeywordReadRepository.findSuggestions(eq("청년"), any(LocalDateTime.class), eq(2), eq(3)))
                .willReturn(List.of("청년 도약계좌", "청년 취업 지원", "청년 주거 지원"));

        List<String> response = policySearchKeywordReadService.getSuggestions("청년", 3);

        then(welfareServiceSearchRepository).should(never()).searchChatCandidates(eq("청년"), eq(5));
        assertThat(response).containsExactly(
                "청년 도약계좌",
                "청년 취업 지원",
                "청년 주거 지원"
        );
    }
}
