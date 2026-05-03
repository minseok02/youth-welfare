package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RecommendationBookmarkCommandService {

    private static final long MAX_BOOKMARKS = 200;

    private final UserRecommendationRepository userRecommendationRepository;
    private final UserRepository userRepository;
    private final WelfareServiceRepository welfareServiceRepository;

    @Transactional
    public void toggleRecommendationBookmark(Long userId, Long recommendationId) {
        String userKey = resolveUserKey(userId);
        UserRecommendation recommendation = userRecommendationRepository.findByIdAndUserKey(recommendationId, userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        recommendation.toggleBookmark();
    }

    @Transactional
    public void togglePolicyBookmark(Long userId, Long serviceId) {
        String userKey = resolveUserKey(userId);
        UserRecommendation recommendation = userRecommendationRepository
                .findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc(userKey, serviceId)
                .orElseGet(() -> createBookmarkPlaceholder(userKey, serviceId));

        if (!recommendation.isBookmarked()
                && userRecommendationRepository.countByUserKeyAndIsBookmarkedTrue(userKey) >= MAX_BOOKMARKS) {
            throw new CustomException(ErrorCode.BOOKMARK_LIMIT_EXCEEDED);
        }
        recommendation.toggleBookmark();
    }

    private UserRecommendation createBookmarkPlaceholder(String userKey, Long serviceId) {
        WelfareService service = welfareServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));

        UserRecommendation placeholder = UserRecommendation.builder()
                .userKey(userKey)
                .service(service)
                .recommendedAt(LocalDateTime.now())
                .build();
        return userRecommendationRepository.save(placeholder);
    }

    private String resolveUserKey(Long userId) {
        return userRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
