package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.repository.ChatPolicyReadCondition;
import com.example.welfare.chat.repository.ChatPolicyReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPolicyServiceTest {

    @Mock
    private ChatPolicyReadRepository chatPolicyReadRepository;

    private ChatPolicyService chatPolicyService;

    @BeforeEach
    void setUp() {
        chatPolicyService = new ChatPolicyService(
                chatPolicyReadRepository,
                new ChatBranchCatalog(),
                new ChatCategoryHintCatalog()
        );
    }

    @Test
    @DisplayName("질문 키워드로 챗봇 정책 후보를 조회한다")
    void findCandidatesSearchesByQuestionKeyword() {
        WelfareService service = createService(1829L, "청년월세 한시 특별지원");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition(
                "서울 월세",
                5,
                null,
                "주거",
                List.of("주거", "월세", "전세", "임대")
        )))
                .thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                        "서울 월세",
                        "MERGED_RESULTS",
                        List.of(service),
                        List.of(),
                        List.of(service)
                ));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("서울 월세");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getServiceId()).isEqualTo(1829L);
        assertThat(candidates.get(0).getTitle()).isEqualTo("청년월세 한시 특별지원");
    }

    @Test
    @DisplayName("검색 결과가 없으면 인기 청년 정책 후보로 fallback 한다")
    void findCandidatesFallsBackWhenSearchHasNoResult() {
        WelfareService fallbackService = createService(2451L, "국민취업지원제도");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition("취업 지원", 3)))
                .thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                        "취업 지원",
                        "POPULAR_FALLBACK",
                        List.of(),
                        List.of(),
                        List.of(fallbackService)
                ));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("취업 지원", 3);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getServiceId()).isEqualTo(2451L);
    }

    @Test
    @DisplayName("질문이 기호만 있으면 검색 대신 fallback 후보를 조회한다")
    void findCandidatesFallsBackWhenQuestionHasNoSearchableToken() {
        WelfareService fallbackService = createService(3001L, "청년 도약 지원");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition("", 5)))
                .thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                        "",
                        "POPULAR_FALLBACK",
                        List.of(),
                        List.of(),
                        List.of(fallbackService)
                ));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("!!! ???");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getServiceId()).isEqualTo(3001L);
    }

    @Test
    @DisplayName("빈 질문으로 후보 조회를 요청하면 입력 오류를 반환한다")
    void findCandidatesThrowsWhenQuestionBlank() {
        assertThatThrownBy(() -> chatPolicyService.findCandidates("   "))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("후보 수 제한은 최대 10건으로 정규화한다")
    void findCandidatesNormalizesLimit() {
        WelfareService service = createService(1829L, "청년월세 한시 특별지원");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition(
                "서울 월세",
                10,
                null,
                "주거",
                List.of("주거", "월세", "전세", "임대")
        )))
                .thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                        "서울 월세",
                        "MERGED_RESULTS",
                        List.of(service),
                        List.of(),
                        List.of(service)
                ));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("서울 월세", 99);

        assertThat(candidates).hasSize(1);
    }

    @Test
    @DisplayName("branchKey가 있으면 branch 조건을 포함해 후보를 조회한다")
    void findCandidatesSearchesByBranchCondition() {
        WelfareService service = createService(1829L, "청년월세 한시 특별지원");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition(
                "주거",
                3,
                "housing-cash",
                "주거",
                List.of("월세", "주거비", "지원금")
        ))).thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                "주거 월세 주거비 지원금",
                "MERGED_RESULTS",
                List.of(service),
                List.of(),
                List.of(service)
        ));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("주거", "housing-cash", 3);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getServiceId()).isEqualTo(1829L);
    }

    @Test
    @DisplayName("trace 조회는 정규화 키워드와 검색 조건을 함께 반환한다")
    void traceCandidatesReturnsMetadata() {
        WelfareService service = createService(77L, "청년 창업 자금");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition(
                "창업 지원",
                4,
                "job-startup",
                "일자리",
                List.of("창업")
        ))).thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                "창업 지원 창업 금융 사업 자금",
                "MERGED_RESULTS",
                List.of(),
                List.of(service),
                List.of(service)
        ));

        ChatPolicyService.CandidateTrace trace = chatPolicyService.traceCandidates("창업 지원", "job-startup", 4);

        assertThat(trace.normalizedKeyword()).isEqualTo("창업 지원");
        assertThat(trace.searchKeyword()).contains("창업");
        assertThat(trace.branchKey()).isEqualTo("job-startup");
        assertThat(trace.preferredCategory()).isEqualTo("일자리");
        assertThat(trace.semanticCandidates()).hasSize(1);
        assertThat(trace.finalCandidates()).hasSize(1);
    }

    @Test
    @DisplayName("trace 조회는 사용자 지역을 검색 조건에 포함한다")
    void traceCandidatesIncludesUserRegionContext() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("chat@example.com")
                .passwordHash("hash")
                .sido("서울특별시")
                .sgg("마포구")
                .regionCode("11440")
                .build();
        WelfareService service = createService(11160L, "서울시 청년 월세 지원");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition(
                "월세 쪽으로 보여줘",
                3,
                "housing-cash",
                "주거",
                List.of("월세"),
                "11440",
                "서울특별시",
                "마포구"
        ))).thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                "월세 주거비 지원금",
                "MERGED_RESULTS",
                List.of(service),
                List.of(),
                List.of(service)
        ));

        ChatPolicyService.CandidateTrace trace =
                chatPolicyService.traceCandidatesForUser("월세 쪽으로 보여줘", "housing-cash", 3, user);

        assertThat(trace.finalCandidates()).extracting(ChatPolicyCandidate::getServiceId)
                .containsExactly(11160L);
    }

    @Test
    @DisplayName("질문에 명시된 지역은 사용자 프로필 지역보다 우선한다")
    void traceCandidatesUsesExplicitQuestionRegionBeforeUserProfileRegion() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("chat@example.com")
                .passwordHash("hash")
                .sido("서울특별시")
                .sgg("마포구")
                .regionCode("11440")
                .build();
        WelfareService service = createService(11436L, "인천광역시 청년 주택임차보증금 이자 지원");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition(
                "인천 중구 청년이 받을 수 있는 주거 지원을 알려줘",
                3,
                null,
                "주거",
                List.of("주거", "월세", "전세", "임대"),
                "28110",
                "인천광역시",
                "중구",
                true
        ))).thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                "인천 중구 청년이 받을 수 있는 주거 지원을 알려줘",
                "MERGED_RESULTS",
                List.of(service),
                List.of(),
                List.of(service)
        ));

        ChatPolicyService.CandidateTrace trace = chatPolicyService.traceCandidatesForUser(
                "인천 중구 청년이 받을 수 있는 주거 지원을 알려줘",
                null,
                3,
                user
        );

        assertThat(trace.finalCandidates()).extracting(ChatPolicyCandidate::getServiceId)
                .containsExactly(11436L);
    }

    @Test
    @DisplayName("중복 시군구명만 있는 질문은 명시 지역 hard filter로 승격하지 않는다")
    void traceCandidatesDoesNotPromoteAmbiguousSggOnlyQuestionToExplicitRegion() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("chat@example.com")
                .passwordHash("hash")
                .sido("서울특별시")
                .sgg("마포구")
                .regionCode("11440")
                .build();
        WelfareService service = createService(77L, "중구 청년 주거 지원");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition(
                "중구 청년 주거 지원",
                3,
                null,
                "주거",
                List.of("주거", "월세", "전세", "임대"),
                "11440",
                "서울특별시",
                "마포구",
                false
        ))).thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                "중구 청년 주거 지원",
                "MERGED_RESULTS",
                List.of(service),
                List.of(),
                List.of(service)
        ));

        ChatPolicyService.CandidateTrace trace = chatPolicyService.traceCandidatesForUser(
                "중구 청년 주거 지원",
                null,
                3,
                user
        );

        assertThat(trace.finalCandidates()).extracting(ChatPolicyCandidate::getServiceId)
                .containsExactly(77L);
    }

    @Test
    @DisplayName("branch가 없어도 금융/생활비 질문은 카테고리 힌트를 붙여 조회한다")
    void findCandidatesAppliesCategoryHintWithoutBranch() {
        WelfareService service = createService(88L, "청년 생활안정 지원금");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition(
                "청년 생활비나 금융 지원이 있나요",
                5,
                null,
                "금융·생활지원",
                List.of("금융", "생활비", "대출", "지원금")
        ))).thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                "청년 생활비나 금융 지원이 있나요 금융 생활비 대출 지원금",
                "MERGED_RESULTS",
                List.of(service),
                List.of(),
                List.of(service)
        ));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("청년 생활비나 금융 지원이 있나요");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getServiceId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("branch가 없어도 창업/사업화자금 질문은 일자리 힌트를 붙여 조회한다")
    void findCandidatesAppliesStartupCategoryHintWithoutBranch() {
        WelfareService service = createService(14349L, "청년창업센터 지원");
        when(chatPolicyReadRepository.traceCandidates(new ChatPolicyReadCondition(
                "청년창업센터 사업화자금 쪽으로 보여줘",
                5,
                null,
                "일자리",
                List.of("창업", "사업", "자금")
        ))).thenReturn(new com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace(
                "청년창업센터 사업화자금 쪽으로 보여줘 창업 사업 자금",
                "MERGED_RESULTS",
                List.of(service),
                List.of(),
                List.of(service)
        ));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("청년창업센터 사업화자금 쪽으로 보여줘");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getServiceId()).isEqualTo(14349L);
    }

    private WelfareService createService(Long serviceId, String title) {
        return WelfareService.builder()
                .id(serviceId)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("source-" + serviceId)
                .title(title)
                .description("설명")
                .supportContent("지원 내용")
                .hostOrg("기관")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }
}
