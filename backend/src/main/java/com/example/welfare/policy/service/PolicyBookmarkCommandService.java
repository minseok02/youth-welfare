package com.example.welfare.policy.service;

import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyBookmarkCommandService {

    private final RecommendationBookmarkCommandService recommendationBookmarkCommandService;

    @Transactional
    public void toggleBookmark(Long userId, Long serviceId) {
        recommendationBookmarkCommandService.togglePolicyBookmark(userId, serviceId);
    }
}
