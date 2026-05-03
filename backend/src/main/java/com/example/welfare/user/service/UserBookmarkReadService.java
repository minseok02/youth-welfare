package com.example.welfare.user.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.recommend.facade.RecommendationReadFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBookmarkReadService {

    private final UserReadService userReadService;
    private final RecommendationReadFacade recommendationReadFacade;

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> getBookmarks(Long userId) {
        String userKey = userReadService.getActiveUserContext(userId).userKey();
        return recommendationReadFacade.findBookmarkedPolicySummaries(userKey);
    }
}
