package com.example.welfare.recommend.dto;

import com.example.welfare.policy.dto.PolicySummaryResponse;

public record SimilarUsersViewedPolicyResponse(
        PolicySummaryResponse policy,
        String reasonLabel
) {
}
