package com.example.welfare.recommend.service;

import com.example.welfare.policy.support.CompatCategorySupport;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.ScoredCandidate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecommendationDiversityService {

    private static final int DIVERSITY_WINDOW_SIZE = 12;
    private static final double REPEAT_BUCKET_PENALTY = 0.03d;
    private static final double CONSECUTIVE_BUCKET_PENALTY = 0.02d;
    private static final Set<String> EXCLUDED_BUCKETS = Set.of("OTHER", "UNSPECIFIED");

    public List<ScoredCandidate> apply(List<ScoredCandidate> rankedCandidates) {
        if (rankedCandidates == null || rankedCandidates.size() < 3) {
            return rankedCandidates == null ? List.of() : rankedCandidates;
        }

        long distinctBuckets = rankedCandidates.stream()
                .map(this::resolveDiversityBucket)
                .filter(bucket -> !EXCLUDED_BUCKETS.contains(bucket))
                .distinct()
                .count();
        if (distinctBuckets < 2) {
            return rankedCandidates;
        }

        Map<String, Integer> bucketCounts = new HashMap<>();
        String previousBucket = null;
        java.util.ArrayList<ScoredCandidate> adjusted = new java.util.ArrayList<>(rankedCandidates.size());
        for (int index = 0; index < rankedCandidates.size(); index++) {
            ScoredCandidate candidate = rankedCandidates.get(index);
            if (index >= DIVERSITY_WINDOW_SIZE) {
                adjusted.add(candidate);
                continue;
            }

            String bucket = resolveDiversityBucket(candidate);
            if (EXCLUDED_BUCKETS.contains(bucket)) {
                previousBucket = bucket;
                adjusted.add(candidate);
                continue;
            }

            int bucketCount = bucketCounts.getOrDefault(bucket, 0);
            double penalty = bucketCount * REPEAT_BUCKET_PENALTY;
            if (bucket.equals(previousBucket)) {
                penalty += CONSECUTIVE_BUCKET_PENALTY;
            }

            bucketCounts.put(bucket, bucketCount + 1);
            previousBucket = bucket;

            if (penalty <= 0d) {
                adjusted.add(candidate);
                continue;
            }

            double adjustedScore = Math.max(candidate.getFinalScore() - penalty, 0d);
            adjusted.add(candidate.withFinalScore(adjustedScore, candidate.isAiFallback()));
        }
        return List.copyOf(adjusted);
    }

    private String resolveDiversityBucket(ScoredCandidate candidate) {
        RecommendationCandidateProjection projection = candidate.getProjection();
        if (projection != null) {
            if (StringUtils.hasText(projection.compatPriorityBucket())) {
                return projection.compatPriorityBucket().trim();
            }
            String projectionBucket = CompatCategorySupport.priorityBucket(projection.unifiedCategoryCompat());
            if (StringUtils.hasText(projectionBucket)) {
                return projectionBucket;
            }
        }

        String serviceBucket = CompatCategorySupport.priorityBucket(candidate.getService().getUnifiedCategory());
        if (StringUtils.hasText(serviceBucket)) {
            return serviceBucket;
        }
        return "UNSPECIFIED";
    }
}
