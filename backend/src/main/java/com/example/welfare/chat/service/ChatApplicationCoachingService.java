package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.dto.response.ChatActionLinkResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.service.PolicyDetailReadService;
import com.example.welfare.policy.service.PolicyLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatApplicationCoachingService {

    private static final int TEXT_SNIPPET_LIMIT = 220;

    private final PolicyLookupService policyLookupService;
    private final PolicyDetailReadService policyDetailReadService;
    private final ChatApplicationActionLinkFactory actionLinkFactory;

    @Transactional(readOnly = true)
    public CoachingContext buildContext(Long serviceId) {
        WelfareService service = policyLookupService.getRequiredService(serviceId);
        PolicyDetailReadService.PolicyDetailAggregate aggregate = policyDetailReadService.getAggregate(serviceId);
        WelfareServiceDetail detail = aggregate.detail();
        List<ChatActionLinkResponse> actionLinks = actionLinkFactory.createLinks(service, detail);
        ChatPolicyCandidate candidate = ChatPolicyCandidate.from(service, detail, actionLinks);
        return new CoachingContext(
                candidate,
                Map.of(service.getId(), buildEvidence(service, detail)),
                buildFallbackAnswer(candidate, actionLinks)
        );
    }

    private String buildEvidence(WelfareService service, WelfareServiceDetail detail) {
        return String.join("\n", List.of(
                line("신청기간", formatPeriod(service.getApplyStartDate(), service.getApplyEndDate())),
                line("신청방법", firstText(detail != null ? detail.getApplyMethodDetail() : null, service.getApplyMethodName())),
                line("신청대상", detail != null ? detail.getTargetDetail() : null),
                line("선정기준", detail != null ? detail.getSelectionCriteria() : null),
                line("제출서류", detail != null ? detail.getFormFiles() : null),
                line("문의처", detail != null ? detail.getContactList() : null)
        )).trim();
    }

    private String buildFallbackAnswer(ChatPolicyCandidate candidate, List<ChatActionLinkResponse> actionLinks) {
        String period = formatPeriod(candidate.getApplyStartDate(), candidate.getApplyEndDate());
        String method = firstText(candidate.getApplyMethodDetail(), candidate.getApplyMethodName(), "원문에서 신청방법을 확인해야 합니다.");
        String documents = firstText(candidate.getFormFiles(), "원문 또는 공고에서 제출서류를 확인해야 합니다.");
        String links = actionLinks.isEmpty()
                ? "공식 링크가 없으면 운영기관 문의처나 정책 원문을 확인하세요."
                : "아래 연결 링크에서 공식 신청, 공고, 관련 사이트를 확인하세요.";

        return """
                %s 신청 준비는 아래 순서로 확인하세요.

                1단계 자격 조건: 신청 대상과 선정 기준을 먼저 확인하세요.
                2단계 신청 기간: %s
                3단계 신청 방법: %s
                4단계 제출서류: %s
                5단계 공식 링크/문의처: %s

                실제 신청 가능 여부와 제출서류는 공식 기관 페이지 또는 담당 기관에서 최종 확인해야 합니다.
                """.formatted(candidate.getTitle(), valueOrFallback(period, "상시 신청 또는 별도 문의"), method, documents, links);
    }

    private String line(String label, String value) {
        String normalized = trimToLength(value, TEXT_SNIPPET_LIMIT);
        return StringUtils.hasText(normalized) ? label + ": " + normalized : "";
    }

    private String formatPeriod(LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            return "%s ~ %s".formatted(start, end);
        }
        if (start != null) {
            return "%s ~".formatted(start);
        }
        if (end != null) {
            return "~ %s".formatted(end);
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return trimToLength(value, TEXT_SNIPPET_LIMIT);
            }
        }
        return null;
    }

    private String valueOrFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public record CoachingContext(
            ChatPolicyCandidate candidate,
            Map<Long, String> evidenceByServiceId,
            String fallbackAnswer
    ) {
    }

}
