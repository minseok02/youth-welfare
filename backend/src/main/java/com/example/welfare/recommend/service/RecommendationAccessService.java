package com.example.welfare.recommend.service;

import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.service.UserKeyLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationAccessService {

    private final UserKeyLookupService userKeyLookupService;
    private final RecommendationResultReadService recommendationResultReadService;

    @Transactional(readOnly = true)
    public List<UserRecommendation> getRecommendations(Long userId, int size) {
        String userKey = userKeyLookupService.findRequired(userId);
        return recommendationResultReadService.findTopRecommendations(userKey, size);
    }
}
