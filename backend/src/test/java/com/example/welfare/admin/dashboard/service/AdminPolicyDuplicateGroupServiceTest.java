package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyDuplicateGroupReviewRequest;
import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import com.example.welfare.admin.dashboard.repository.AdminPolicyDuplicateGroupReadRepository;
import com.example.welfare.policy.entity.PolicyDuplicateReviewRecord;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyDuplicateReviewRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminPolicyDuplicateGroupServiceTest {

    @Mock
    private AdminPolicyDuplicateGroupReadRepository readRepository;

    @Mock
    private PolicyDuplicateReviewRecordRepository reviewRecordRepository;

    @InjectMocks
    private AdminPolicyDuplicateGroupService service;

    @Test
    @DisplayName("정책 중복 queue는 열린 묶음 count와 최근 목록을 함께 반환한다")
    void getRecentGroupsReturnsOpenSummary() {
        given(readRepository.countOpenGroups()).willReturn(5L);
        given(readRepository.countRecentOpenGroups24h()).willReturn(2L);
        given(readRepository.countOpenDuplicateRows()).willReturn(17L);
        given(readRepository.findGroups(AdminQueueStatusFilter.OPEN, 5))
                .willReturn(List.of(new AdminPolicyDuplicateGroupReadRepository.DuplicateGroupRow(
                        "YOUTH",
                        "청년문화예술패스",
                        "",
                        null,
                        "exact_duplicate_candidate",
                        3,
                        "A, B, C",
                        LocalDateTime.of(2026, 6, 4, 10, 0),
                        null,
                        null,
                        null
                )));

        var response = service.getRecentGroups(5, AdminQueueStatusFilter.OPEN);

        assertThat(response.openGroupCount()).isEqualTo(5L);
        assertThat(response.recentOpenGroupCount24h()).isEqualTo(2L);
        assertThat(response.openDuplicateRowCount()).isEqualTo(17L);
        assertThat(response.recentGroups()).hasSize(1);
        assertThat(response.recentGroups().get(0).status()).isEqualTo("OPEN");
        assertThat(response.recentGroups().get(0).reviewClass()).isEqualTo("exact_duplicate_candidate");
    }

    @Test
    @DisplayName("정책 중복 묶음을 처리완료하면 review record를 upsert 한다")
    void markReviewedUpsertsRecord() {
        given(reviewRecordRepository.findBySourceTypeAndTitleAndHostOrgKey(
                WelfareService.SourceType.YOUTH,
                "청년문화예술패스",
                ""
        )).willReturn(Optional.empty());
        given(reviewRecordRepository.save(any(PolicyDuplicateReviewRecord.class)))
                .willAnswer(invocation -> {
                    PolicyDuplicateReviewRecord record = invocation.getArgument(0);
                    return PolicyDuplicateReviewRecord.builder()
                            .id(19L)
                            .sourceType(record.getSourceType())
                            .title(record.getTitle())
                            .hostOrgKey(record.getHostOrgKey())
                            .hostOrgLabel(record.getHostOrgLabel())
                            .reviewNote(record.getReviewNote())
                            .reviewedByUserKey(record.getReviewedByUserKey())
                            .reviewedAt(record.getReviewedAt())
                            .build();
                });

        var response = service.markReviewed(
                new AdminPolicyDuplicateGroupReviewRequest(
                        "YOUTH",
                        "청년문화예술패스",
                        "",
                        null,
                        "중앙 중복 묶음 확인"
                ),
                "admin-user-key"
        );

        assertThat(response.id()).isEqualTo(19L);
        assertThat(response.status()).isEqualTo("REVIEWED");
        assertThat(response.reviewNote()).isEqualTo("중앙 중복 묶음 확인");
        assertThat(response.reviewedByUserKey()).isEqualTo("admin-user-key");
        assertThat(response.reviewedAt()).isNotNull();
    }
}
