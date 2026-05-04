package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserProfile;

import java.util.List;

public record RecommendationUserReadModel(
        UserProfile profile,
        List<UserAttributeReadModel> attributes,
        List<UserPriorityReadModel> priorities
) {
}
