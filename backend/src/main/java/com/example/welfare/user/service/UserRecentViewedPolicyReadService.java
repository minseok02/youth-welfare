package com.example.welfare.user.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.RecentPolicyView;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.RecentPolicyViewRepository;
import com.example.welfare.policy.service.PolicyPresentationReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRecentViewedPolicyReadService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    private final ActiveUserReadService activeUserReadService;
    private final RecentPolicyViewRepository recentPolicyViewRepository;
    private final PolicyPresentationReadService policyPresentationReadService;

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> getRecentViewedPolicies(Long userId, Integer limit) {
        String userKey = activeUserReadService.getActiveUserContext(userId).userKey();
        int resolvedLimit = normalizeLimit(limit);

        List<WelfareService> services = recentPolicyViewRepository.findRecentViewsByUserKey(
                        userKey,
                        PageRequest.of(0, resolvedLimit)
                ).stream()
                .map(RecentPolicyView::getService)
                .toList();

        if (services.isEmpty()) {
            return List.of();
        }

        return policyPresentationReadService.buildSummaryResponses(userId, services);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
