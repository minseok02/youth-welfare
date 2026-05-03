package com.example.welfare.user.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBookmarkReadService {

    private final UserReadService userReadService;
    private final UserRecommendationRepository userRecommendationRepository;
    private final CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> getBookmarks(Long userId) {
        String userKey = userReadService.getActiveUserContext(userId).userKey();
        List<UserRecommendation> bookmarks = userRecommendationRepository.findLatestBookmarkedByUserKey(userKey);
        java.util.Map<Long, com.example.welfare.recommend.dto.RecommendationCandidateProjection> projections =
                canonicalRecommendationReadModelRepository.findByServiceIds(
                        bookmarks.stream().map(bookmark -> bookmark.getService().getId()).toList()
                );
        return bookmarks.stream()
                .map(UserRecommendation::getService)
                .map(service -> PolicySummaryResponse.from(service, true, projections.get(service.getId())))
                .toList();
    }
}
