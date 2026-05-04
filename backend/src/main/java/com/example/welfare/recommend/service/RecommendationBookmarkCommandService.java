package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.service.PolicyLookupService;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationBookmarkCommandRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RecommendationBookmarkCommandService {

    private static final long MAX_BOOKMARKS = 200;

    private final RecommendationBookmarkCommandRepository recommendationBookmarkCommandRepository;
    private final UserKeyLookupService userKeyLookupService;
    private final PolicyLookupService policyLookupService;

    @Transactional
    public void toggleRecommendationBookmark(Long userId, Long recommendationId) {
        String userKey = resolveUserKey(userId);
        UserRecommendation recommendation = recommendationBookmarkCommandRepository
                .findOwnedRecommendation(recommendationId, userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        recommendation.toggleBookmark();
    }

    @Transactional
    public void togglePolicyBookmark(Long userId, Long serviceId) {
        String userKey = resolveUserKey(userId);
        UserRecommendation recommendation = recommendationBookmarkCommandRepository
                .findLatestRecommendation(userKey, serviceId)
                .orElseGet(() -> createBookmarkPlaceholder(userKey, serviceId));

        if (!recommendation.isBookmarked()
                && recommendationBookmarkCommandRepository.countBookmarked(userKey) >= MAX_BOOKMARKS) {
            throw new CustomException(ErrorCode.BOOKMARK_LIMIT_EXCEEDED);
        }
        recommendation.toggleBookmark();
    }

    private UserRecommendation createBookmarkPlaceholder(String userKey, Long serviceId) {
        WelfareService service = policyLookupService.getRequiredService(serviceId);

        UserRecommendation placeholder = UserRecommendation.builder()
                .userKey(userKey)
                .service(service)
                .recommendedAt(LocalDateTime.now())
                .build();
        return recommendationBookmarkCommandRepository.save(placeholder);
    }

    private String resolveUserKey(Long userId) {
        return userKeyLookupService.findRequired(userId);
    }
}
