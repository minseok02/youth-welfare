package com.example.welfare.collect.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CollectListChangePolicy {

    private final int newCountThreshold;
    private final double newShareThreshold;
    private final int changedCountThreshold;
    private final double changedShareThreshold;
    private final double countDropShareThreshold;
    private final int maxCandidatesPerRun;

    public CollectListChangePolicy(
            @Value("${collect.list.diff.force-detail.new-count-threshold:30}") int newCountThreshold,
            @Value("${collect.list.diff.force-detail.new-share-threshold:0.02}") double newShareThreshold,
            @Value("${collect.list.diff.force-detail.changed-count-threshold:100}") int changedCountThreshold,
            @Value("${collect.list.diff.force-detail.changed-share-threshold:0.05}") double changedShareThreshold,
            @Value("${collect.list.diff.guard.count-drop-share-threshold:0.15}") double countDropShareThreshold,
            @Value("${collect.list.diff.force-detail.max-candidates-per-run:50}") int maxCandidatesPerRun
    ) {
        this.newCountThreshold = Math.max(1, newCountThreshold);
        this.newShareThreshold = Math.max(0.0, newShareThreshold);
        this.changedCountThreshold = Math.max(1, changedCountThreshold);
        this.changedShareThreshold = Math.max(0.0, changedShareThreshold);
        this.countDropShareThreshold = Math.max(0.0, countDropShareThreshold);
        this.maxCandidatesPerRun = Math.max(0, maxCandidatesPerRun);
    }

    public Decision decide(CollectListDiffService.CollectListDiff diff) {
        if (diff.baseline()) {
            return Decision.noop("BASELINE_ONLY", List.of());
        }

        double newShare = share(diff.newCount(), diff.totalCount());
        double changedShare = share(diff.changedCount(), diff.totalCount());
        int previousTotal = diff.totalCount() - diff.newCount() + diff.missingCount();
        double missingShare = share(diff.missingCount(), previousTotal);

        if (missingShare >= countDropShareThreshold && diff.missingCount() > diff.changedOrNewCount()) {
            return Decision.guarded(
                    "COUNT_DROP_GUARDED missing=%d previousTotal=%d missingShare=%.4f"
                            .formatted(diff.missingCount(), previousTotal, missingShare)
            );
        }

        boolean newThresholdMet = diff.newCount() >= newCountThreshold || newShare >= newShareThreshold;
        boolean changedThresholdMet = diff.changedCount() >= changedCountThreshold || changedShare >= changedShareThreshold;
        if (!newThresholdMet && !changedThresholdMet) {
            return Decision.noop(
                    "BELOW_THRESHOLD new=%d newShare=%.4f changed=%d changedShare=%.4f"
                            .formatted(diff.newCount(), newShare, diff.changedCount(), changedShare),
                    List.of()
            );
        }

        List<String> candidates = diff.prioritizedDetailCandidates(maxCandidatesPerRun);
        if (candidates.isEmpty()) {
            return Decision.noop("NO_DETAIL_CANDIDATES", List.of());
        }
        return Decision.force(
                "FORCE_DETAIL new=%d newShare=%.4f changed=%d changedShare=%.4f candidates=%d"
                        .formatted(diff.newCount(), newShare, diff.changedCount(), changedShare, candidates.size()),
                candidates
        );
    }

    private double share(int value, int denominator) {
        if (value <= 0 || denominator <= 0) {
            return 0.0;
        }
        return (double) value / (double) denominator;
    }

    public record Decision(
            boolean forceDetail,
            boolean guardedCountDrop,
            String reason,
            List<String> detailCandidateSourceIds
    ) {
        private static Decision force(String reason, List<String> candidateSourceIds) {
            return new Decision(true, false, reason, List.copyOf(candidateSourceIds));
        }

        private static Decision guarded(String reason) {
            return new Decision(false, true, reason, List.of());
        }

        private static Decision noop(String reason, List<String> candidateSourceIds) {
            return new Decision(false, false, reason, List.copyOf(candidateSourceIds));
        }
    }
}
