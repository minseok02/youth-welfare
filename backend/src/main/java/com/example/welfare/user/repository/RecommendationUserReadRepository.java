package com.example.welfare.user.repository;

import java.util.Optional;

public interface RecommendationUserReadRepository {

    Optional<RecommendationUserReadModel> findByUserKey(String userKey);
}
