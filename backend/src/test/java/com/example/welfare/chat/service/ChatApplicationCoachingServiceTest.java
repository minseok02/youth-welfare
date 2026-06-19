package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.response.ChatActionLinkResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.service.PolicyDetailReadService;
import com.example.welfare.policy.service.PolicyLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatApplicationCoachingServiceTest {

    @Mock
    private PolicyLookupService policyLookupService;
    @Mock
    private PolicyDetailReadService policyDetailReadService;

    @Test
    @DisplayName("신청 코칭 context는 정책 상세와 공식/참고 링크를 신청 준비용 후보로 변환한다")
    void buildContextMapsPolicyDetailAndActionLinks() {
        ChatApplicationCoachingService service = new ChatApplicationCoachingService(
                policyLookupService,
                policyDetailReadService,
                new ChatApplicationActionLinkFactory(new ObjectMapper())
        );
        WelfareService policy = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("coach-11")
                .title("청년 월세 지원")
                .description("월세 부담 완화")
                .supportContent("월세 지원")
                .applyMethodName("온라인 신청")
                .applyStartDate(LocalDate.of(2026, 6, 1))
                .applyEndDate(LocalDate.of(2026, 6, 30))
                .detailUrl("https://detail.example.com")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        WelfareServiceDetail detail = WelfareServiceDetail.builder()
                .service(policy)
                .targetDetail("만 19~34세 청년")
                .selectionCriteria("소득 심사")
                .applyMethodDetail("온라인 신청 후 서류 제출")
                .formFiles("신청서, 주민등록등본")
                .referenceUrlsJson("""
                        [
                          {"url":"https://apply.example.com","type":"APPLY","label":"신청 URL","sourceField":"aplyUrlAddr"},
                          {"url":"https://notice.example.com","type":"REFERENCE","label":"공고문","sourceField":"refUrlAddr1"}
                        ]
                        """)
                .build();

        given(policyLookupService.getRequiredService(11L)).willReturn(policy);
        given(policyDetailReadService.getAggregate(11L))
                .willReturn(new PolicyDetailReadService.PolicyDetailAggregate(detail, List.of(), List.of()));

        ChatApplicationCoachingService.CoachingContext context = service.buildContext(11L);

        assertThat(context.candidate().getTitle()).isEqualTo("청년 월세 지원");
        assertThat(context.candidate().getApplyMethodDetail()).isEqualTo("온라인 신청 후 서류 제출");
        assertThat(context.evidenceByServiceId().get(11L)).contains("신청기간: 2026-06-01 ~ 2026-06-30");
        assertThat(context.fallbackAnswer()).contains("1. 자격 조건").contains("제출서류");
        assertThat(context.candidate().getActionLinks())
                .extracting(ChatActionLinkResponse::getType)
                .containsExactly("OFFICIAL_APPLY", "DOCUMENTS", "RELATED_SITE");
    }
}
