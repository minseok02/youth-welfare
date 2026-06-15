package com.example.welfare.recommend.repository;

public record SimilarUsersViewedPolicyCandidate(
        Long serviceId,
        int similarUserCount,
        int recentViewCount
) {
}
