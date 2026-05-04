package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.service.RecommendationBookmarkReadService;
import com.example.welfare.recommend.service.RecommendationProjectionReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PolicyPresentationReadService {

    private final RecommendationBookmarkReadService recommendationBookmarkReadService;
    private final RecommendationProjectionReadService recommendationProjectionReadService;

    @Transactional(readOnly = true)
    public Page<PolicySummaryResponse> buildSummaryPage(Long userId, Page<WelfareService> page) {
        Set<Long> bookmarkedServiceIds = recommendationBookmarkReadService.findBookmarkedServiceIds(userId, page.getContent());
        Map<Long, RecommendationCandidateProjection> projections = findProjections(page.getContent());
        return page.map(service -> PolicySummaryResponse.from(
                service,
                bookmarkedServiceIds.contains(service.getId()),
                projections.get(service.getId())
        ));
    }

    @Transactional(readOnly = true)
    public PolicyDetailPresentation buildDetailPresentation(Long userId, WelfareService service) {
        boolean bookmarked = recommendationBookmarkReadService.findBookmarkedServiceIds(userId, List.of(service))
                .contains(service.getId());
        RecommendationCandidateProjection projection = recommendationProjectionReadService
                .findCandidateProjectionsByServices(List.of(service))
                .get(service.getId());
        return new PolicyDetailPresentation(bookmarked, projection);
    }

    @Transactional(readOnly = true)
    public Map<Long, RecommendationCandidateProjection> findProjections(List<WelfareService> services) {
        if (services == null || services.isEmpty()) {
            return Map.of();
        }
        return recommendationProjectionReadService.findCandidateProjectionsByServices(services);
    }

    public record PolicyDetailPresentation(
            boolean bookmarked,
            RecommendationCandidateProjection projection
    ) {
    }
}
