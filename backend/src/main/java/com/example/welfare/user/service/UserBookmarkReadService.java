package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBookmarkReadService {

    private final UserRepository userRepository;
    private final UserRecommendationRepository userRecommendationRepository;
    private final CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> getBookmarks(Long userId) {
        findActiveUser(userId);
        String userKey = resolveUserKey(userId);
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

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return user;
    }

    private String resolveUserKey(Long userId) {
        return userRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
