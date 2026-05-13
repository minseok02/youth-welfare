package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyCategoryAuditResponse;
import com.example.welfare.policy.repository.PolicyCategoryAuditReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PolicyCategoryAuditServiceTest {

    private final PolicyCategoryAuditReadRepository repository = mock(PolicyCategoryAuditReadRepository.class);

    @Test
    @DisplayName("category audit service는 집계 결과를 응답 DTO로 묶는다")
    void readAuditBuildsResponse() {
        given(repository.fetchTotalPolicyCount()).willReturn(3925L);
        given(repository.fetchSearchablePolicyCount()).willReturn(2544L);
        given(repository.fetchUnifiedCategoryCounts()).willReturn(List.of(
                new PolicyCategoryAuditResponse.CategoryCount("일자리", 903L, 903L),
                new PolicyCategoryAuditResponse.CategoryCount("금융·생활지원", 501L, 500L)
        ));
        given(repository.fetchYouthBroadCategoryMappings()).willReturn(List.of(
                new PolicyCategoryAuditResponse.SourceCategoryMappingCount("복지문화", "문화·여가", 49L),
                new PolicyCategoryAuditResponse.SourceCategoryMappingCount("금융·복지·문화", "건강·의료", 11L)
        ));

        PolicyCategoryAuditService service = new PolicyCategoryAuditService(repository);

        PolicyCategoryAuditResponse response = service.readAudit();

        assertThat(response.totalPolicyCount()).isEqualTo(3925L);
        assertThat(response.searchablePolicyCount()).isEqualTo(2544L);
        assertThat(response.unifiedCategoryCounts()).hasSize(2);
        assertThat(response.youthBroadCategoryMappings()).hasSize(2);
    }
}
