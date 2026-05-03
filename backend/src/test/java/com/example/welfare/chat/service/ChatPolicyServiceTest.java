package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.repository.ChatPolicyReadCondition;
import com.example.welfare.chat.repository.ChatPolicyReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
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
        chatPolicyService = new ChatPolicyService(chatPolicyReadRepository);
    }

    @Test
    @DisplayName("질문 키워드로 챗봇 정책 후보를 조회한다")
    void findCandidatesSearchesByQuestionKeyword() {
        WelfareService service = createService(1829L, "청년월세 한시 특별지원");
        when(chatPolicyReadRepository.findCandidates(new ChatPolicyReadCondition("+서울 +월세", 5)))
                .thenReturn(List.of(service));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("서울 월세");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getServiceId()).isEqualTo(1829L);
        assertThat(candidates.get(0).getTitle()).isEqualTo("청년월세 한시 특별지원");
    }

    @Test
    @DisplayName("검색 결과가 없으면 인기 청년 정책 후보로 fallback 한다")
    void findCandidatesFallsBackWhenSearchHasNoResult() {
        WelfareService fallbackService = createService(2451L, "국민취업지원제도");
        when(chatPolicyReadRepository.findCandidates(new ChatPolicyReadCondition("+취업 +지원", 3)))
                .thenReturn(List.of(fallbackService));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("취업 지원", 3);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getServiceId()).isEqualTo(2451L);
    }

    @Test
    @DisplayName("질문이 기호만 있으면 검색 대신 fallback 후보를 조회한다")
    void findCandidatesFallsBackWhenQuestionHasNoSearchableToken() {
        WelfareService fallbackService = createService(3001L, "청년 도약 지원");
        when(chatPolicyReadRepository.findCandidates(new ChatPolicyReadCondition("", 5)))
                .thenReturn(List.of(fallbackService));

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
        when(chatPolicyReadRepository.findCandidates(new ChatPolicyReadCondition("+서울 +월세", 10)))
                .thenReturn(List.of(createService(1829L, "청년월세 한시 특별지원")));

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates("서울 월세", 99);

        assertThat(candidates).hasSize(1);
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
