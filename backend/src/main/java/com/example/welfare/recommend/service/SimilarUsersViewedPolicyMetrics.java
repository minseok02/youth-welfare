package com.example.welfare.recommend.service;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimilarUsersViewedPolicyMetrics {

    static final String REQUEST_COUNTER = "recommendation.similar_users_viewed.requests";
    static final String CANDIDATE_SUMMARY = "recommendation.similar_users_viewed.candidates";
    static final String RESULT_SUMMARY = "recommendation.similar_users_viewed.results";

    private final MeterRegistry meterRegistry;

    public void recordNoSignal() {
        incrementRequest("no_signal");
        recordCandidateCount(0);
        recordResultCount(0);
    }

    public void recordResult(int candidateCount, int resultCount) {
        incrementRequest(resultCount > 0 ? "returned" : "empty");
        recordCandidateCount(candidateCount);
        recordResultCount(resultCount);
    }

    private void incrementRequest(String outcome) {
        meterRegistry.counter(REQUEST_COUNTER, "outcome", outcome).increment();
    }

    private void recordCandidateCount(int count) {
        summary(CANDIDATE_SUMMARY).record(count);
    }

    private void recordResultCount(int count) {
        summary(RESULT_SUMMARY).record(count);
    }

    private DistributionSummary summary(String name) {
        return DistributionSummary.builder(name)
                .publishPercentileHistogram(false)
                .register(meterRegistry);
    }
}
