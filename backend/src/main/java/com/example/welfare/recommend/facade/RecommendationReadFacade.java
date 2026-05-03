package com.example.welfare.recommend.facade;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationReadFacade {

    private final UserRecommendationRepository userRecommendationRepository;
    private final CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;
    private final UserKeyLookupService userKeyLookupService;

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

        return new HashSet<>(userRecommendationRepository.findLatestBookmarkedServiceIdsByUserKey(userKey, serviceIds));
    }

    public List<PolicySummaryResponse> findBookmarkedPolicySummaries(String userKey) {
        List<UserRecommendation> bookmarks = userRecommendationRepository.findLatestBookmarkedByUserKey(userKey);
        Map<Long, RecommendationCandidateProjection> projections =
                canonicalRecommendationReadModelRepository.findByServiceIds(
                        bookmarks.stream().map(bookmark -> bookmark.getService().getId()).toList()
                );
        return bookmarks.stream()
                .map(UserRecommendation::getService)
                .map(service -> PolicySummaryResponse.from(service, true, projections.get(service.getId())))
                .toList();
    }
}
