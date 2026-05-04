package com.example.welfare.recommend.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationSummaryReadRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationBookmarkReadService {

    private final RecommendationSummaryReadRepository recommendationSummaryReadRepository;
    private final RecommendationProjectionReadService recommendationProjectionReadService;
    private final UserKeyLookupService userKeyLookupService;

    @Transactional(readOnly = true)
    public Set<Long> findBookmarkedServiceIds(Long userId, List<WelfareService> services) {
        if (userId == null || services == null || services.isEmpty()) {
            return Collections.emptySet();
        }
        String userKey = userKeyLookupService.findNullable(userId);
        if (userKey == null) {
            return Collections.emptySet();
        }

        List<Long> serviceIds = services.stream()
                .map(WelfareService::getId)
                .toList();

        return new HashSet<>(recommendationSummaryReadRepository.findLatestBookmarkedServiceIds(userKey, serviceIds));
    }

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> findBookmarkedPolicySummaries(String userKey) {
        List<UserRecommendation> bookmarks = recommendationSummaryReadRepository.findLatestBookmarkedRecommendations(userKey);
        Map<Long, RecommendationCandidateProjection> projections =
                recommendationProjectionReadService.findCandidateProjections(bookmarks);
        return bookmarks.stream()
                .map(UserRecommendation::getService)
                .map(service -> PolicySummaryResponse.from(service, true, projections.get(service.getId())))
                .toList();
    }
}
