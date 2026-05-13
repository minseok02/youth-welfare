package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyCategoryAuditResponse;
import com.example.welfare.policy.repository.PolicyCategoryAuditReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PolicyCategoryAuditService {

    private final PolicyCategoryAuditReadRepository policyCategoryAuditReadRepository;

    public PolicyCategoryAuditResponse readAudit() {
        return new PolicyCategoryAuditResponse(
                policyCategoryAuditReadRepository.fetchTotalPolicyCount(),
                policyCategoryAuditReadRepository.fetchSearchablePolicyCount(),
                policyCategoryAuditReadRepository.fetchUnifiedCategoryCounts(),
                policyCategoryAuditReadRepository.fetchYouthBroadCategoryMappings()
        );
    }
}
