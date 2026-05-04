package com.example.welfare.user.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.recommend.service.RecommendationBookmarkReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBookmarkReadService {

    private final ActiveUserReadService activeUserReadService;
    private final RecommendationBookmarkReadService recommendationBookmarkReadService;

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> getBookmarks(Long userId) {
        String userKey = activeUserReadService.getActiveUserContext(userId).userKey();
        return recommendationBookmarkReadService.findBookmarkedPolicySummaries(userKey);
    }
}
