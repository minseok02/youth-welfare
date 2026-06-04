package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import com.example.welfare.admin.dashboard.repository.AdminPolicyLinkReviewReadRepository;
import com.example.welfare.policy.entity.PolicyLinkReviewRecord;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyLinkReviewRecordRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminPolicyLinkReviewServiceTest {

    @Mock
    private AdminPolicyLinkReviewReadRepository readRepository;

    @Mock
    private PolicyLinkReviewRecordRepository reviewRecordRepository;

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @InjectMocks
    private AdminPolicyLinkReviewService service;

    @Test
    @DisplayName("정책 링크 review queue는 열린 건수와 최근 목록을 함께 반환한다")
    void getRecentReviewsReturnsOpenSummary() {
        given(readRepository.countOpenReviews()).willReturn(164L);
        given(readRepository.countRecentOpenReviews24h()).willReturn(12L);
        given(readRepository.findRows(AdminQueueStatusFilter.OPEN, 5))
                .willReturn(List.of(new AdminPolicyLinkReviewReadRepository.LinkReviewRow(
                        91L,
                        "청년인턴 사업",
                        "YOUTH",
                        "20260504005400113130",
                        "청년정책관",
                        "충청남도 및 충남경제진흥원",
                        "일자리",
                        "취업",
                        null,
                        null,
                        LocalDateTime.of(2026, 6, 4, 9, 0),
                        null,
                        null,
                        null
                )));

        var response = service.getRecentReviews(5, AdminQueueStatusFilter.OPEN);

        assertThat(response.openCount()).isEqualTo(164L);
        assertThat(response.recentOpenCount24h()).isEqualTo(12L);
        assertThat(response.recentReviews()).hasSize(1);
        assertThat(response.recentReviews().get(0).status()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("정책 링크 review를 처리완료하면 record를 upsert 한다")
    void markReviewedUpsertsRecord() {
        WelfareService policy = WelfareService.builder()
                .id(91L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("20260504005400113130")
                .title("청년인턴 사업")
                .build();
        given(welfareServiceRepository.findById(91L)).willReturn(Optional.of(policy));
        given(reviewRecordRepository.findByPolicyId(91L)).willReturn(Optional.empty());
        given(reviewRecordRepository.save(any(PolicyLinkReviewRecord.class)))
                .willAnswer(invocation -> {
                    PolicyLinkReviewRecord record = invocation.getArgument(0);
                    return PolicyLinkReviewRecord.builder()
                            .id(77L)
                            .policy(record.getPolicy())
                            .reviewNote(record.getReviewNote())
                            .reviewedByUserKey(record.getReviewedByUserKey())
                            .reviewedAt(record.getReviewedAt())
                            .build();
                });

        var response = service.markReviewed(91L, "admin-user-key", "원천 링크 없음 확인");

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.status()).isEqualTo("REVIEWED");
        assertThat(response.reviewNote()).isEqualTo("원천 링크 없음 확인");
        assertThat(response.reviewedByUserKey()).isEqualTo("admin-user-key");
        assertThat(response.reviewedAt()).isNotNull();
    }
}
