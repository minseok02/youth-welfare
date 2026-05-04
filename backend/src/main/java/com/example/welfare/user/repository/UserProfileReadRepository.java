package com.example.welfare.user.repository;

import java.util.Optional;

public interface UserProfileReadRepository {

    Optional<UserProfileAggregateReadModel> findProfileAggregateByUserKey(String userKey);
}
