package com.example.welfare.recommend.repository;

import java.util.List;

public interface SimilarUsersViewedPolicyReadRepository {

    List<SimilarUsersViewedPolicyCandidate> findCandidates(SimilarUsersViewedPolicyQuery query);
}
