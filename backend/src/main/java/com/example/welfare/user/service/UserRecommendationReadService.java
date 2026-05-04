package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.RecommendationUserReadRepository;
import com.example.welfare.user.repository.UserAttributeReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRecommendationReadService {

    private final ActiveUserReadService activeUserReadService;
    private final AuthIdentityReadService authIdentityReadService;
    private final RecommendationUserReadRepository recommendationUserReadRepository;

    @Transactional(readOnly = true)
    public RecommendationUserSnapshot getRecommendationSnapshot(Long userId) {
        return getRecommendationContext(userId).snapshot();
    }

    @Transactional(readOnly = true)
    public RecommendationReadContext getRecommendationContext(Long userId) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        User user = activeUserContext.user();
        String userKey = authIdentityReadService.requireActiveUserKey(activeUserContext.userKey());
        var aggregate = recommendationUserReadRepository.findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        UserProfile profile = aggregate.profile();
        List<String> interestFields = aggregate.attributes().stream()
                .filter(attr -> UserAttribute.AttrType.INTEREST_FIELD.name().equals(attr.getAttrType()))
                .map(UserAttributeReadModel::getAttrValue)
                .toList();
        List<String> targetTypes = aggregate.attributes().stream()
                .filter(attr -> UserAttribute.AttrType.TARGET_TYPE.name().equals(attr.getAttrType()))
                .map(UserAttributeReadModel::getAttrValue)
                .toList();
        List<PriorityPreference> priorities = aggregate.priorities().stream()
                .map(priority -> new PriorityPreference(priority.getPriorityRank(), priority.getCode(), priority.getWeight()))
                .toList();

        RecommendationUserSnapshot snapshot = new RecommendationUserSnapshot(
                userId,
                userKey,
                profile.getAge(),
                profile.getAgeBand(),
                profile.getSido(),
                profile.getSgg(),
                profile.getRegionCode(),
                profile.getIncomeLevel(),
                profile.getHouseholdType(),
                profile.getEmploymentStatus(),
                profile.getDisplayCount(),
                profile.getNotificationMinScore(),
                interestFields,
                targetTypes,
                priorities
        );
        return new RecommendationReadContext(user, snapshot);
    }

    public record RecommendationReadContext(
            User user,
            RecommendationUserSnapshot snapshot
    ) {
    }
}
