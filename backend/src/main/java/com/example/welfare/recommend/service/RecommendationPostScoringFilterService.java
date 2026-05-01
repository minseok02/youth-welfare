package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.ScoredCandidate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * rule/ai scoring 이후 적용하는 후보 후처리 필터를 한 곳에 모은다.
 */
@Service
public class RecommendationPostScoringFilterService {

    public List<ScoredCandidate> filterSpecialTargetMismatches(List<ScoredCandidate> scoredCandidates) {
        if (scoredCandidates == null || scoredCandidates.isEmpty()) {
            return List.of();
        }
        return scoredCandidates.stream()
                .filter(candidate -> !candidate.isHasSpecialTargetMismatch())
                .toList();
    }
}
