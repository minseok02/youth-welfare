package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserProfile;

import java.util.List;

public record UserProfileAggregateReadModel(
        UserProfile profile,
        UserPiiReadModel pii,
        List<UserAttributeReadModel> attributes,
        List<UserPriorityReadModel> priorities
) {
}
