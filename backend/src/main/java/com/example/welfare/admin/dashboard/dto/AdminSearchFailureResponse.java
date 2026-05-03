package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminSearchFailureResponse(
        LocalDateTime generatedAt,
        int windowDays,
        long totalZeroResultSearches,
        List<KeywordCount> zeroResultKeywords,
        List<RegionCount> zeroResultRegions,
        List<FilterPatternCount> zeroResultFilterPatterns,
        List<SearchFailureSample> recentSamples,
        List<RetryGroup> retryGroups
) {

    public record KeywordCount(
            String keyword,
            long searchCount
    ) {
    }

    public record RegionCount(
            String sido,
            String sgg,
            long searchCount
    ) {
    }

    public record FilterPatternCount(
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            long searchCount
    ) {
    }

    public record SearchFailureSample(
            String keyword,
            String sido,
            String sgg,
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            LocalDateTime searchedAt
    ) {
    }

    public record RetryGroup(
            String actorType,
            String actorKey,
            String keyword,
            String sido,
            String sgg,
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            long retryCount,
            LocalDateTime firstSearchedAt,
            LocalDateTime latestSearchedAt
    ) {
    }
}
