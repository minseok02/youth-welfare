package com.example.welfare.policy.repository;

import com.example.welfare.policy.dto.PolicyCategoryAuditResponse;

import java.util.List;

public interface PolicyCategoryAuditReadRepository {

    long fetchTotalPolicyCount();

    long fetchSearchablePolicyCount();

    List<PolicyCategoryAuditResponse.CategoryCount> fetchUnifiedCategoryCounts();

    List<PolicyCategoryAuditResponse.SourceCategoryMappingCount> fetchYouthBroadCategoryMappings();
}
