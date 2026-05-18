package com.example.welfare.collect.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CollectRuntimeLaneConfigCatalog {

    private final long listRequestIntervalMs;
    private final int listRetryMaxAttempts;
    private final long listRetryBaseBackoffMs;
    private final int listMaxConsecutiveRateLimitHits;
    private final long listRateLimitCooldownMs;
    private final long listLocalRateLimitOpenCircuitMs;
    private final int listMaxItemsPerRun;
    private final int gov24ListMaxItemsPerRun;
    private final int detailCentralMaxCallsPerRun;
    private final int detailLocalMaxCallsPerRun;
    private final int gov24DetailMaxCallsPerRun;
    private final int gov24SupportConditionsMaxCallsPerRun;
    private final long detailRequestIntervalMs;
    private final int detailRetryMaxAttempts;
    private final long detailRetryBaseBackoffMs;
    private final int detailMaxConsecutiveRateLimitHits;
    private final long youthDetailRequestIntervalMs;
    private final long lockLeaseMinutes;
    private final long lockHeartbeatSeconds;

    public CollectRuntimeLaneConfigCatalog(
            @Value("${collect.list.request-interval-ms:300}") long listRequestIntervalMs,
            @Value("${collect.list.retry.max-attempts:3}") int listRetryMaxAttempts,
            @Value("${collect.list.retry.base-backoff-ms:1000}") long listRetryBaseBackoffMs,
            @Value("${collect.list.max-consecutive-rate-limit-hits:3}") int listMaxConsecutiveRateLimitHits,
            @Value("${collect.list.rate-limit-cooldown-ms:10000}") long listRateLimitCooldownMs,
            @Value("${collect.list.local-rate-limit-open-circuit-ms:1800000}") long listLocalRateLimitOpenCircuitMs,
            @Value("${collect.list.max-items-per-run:10000}") int listMaxItemsPerRun,
            @Value("${gov24.max-items-per-run:20000}") int gov24ListMaxItemsPerRun,
            @Value("${collect.detail.central.max-calls-per-run:10000}") int detailCentralMaxCallsPerRun,
            @Value("${collect.detail.local.max-calls-per-run:10000}") int detailLocalMaxCallsPerRun,
            @Value("${collect.detail.gov24.max-calls-per-run:50}") int gov24DetailMaxCallsPerRun,
            @Value("${collect.support-conditions.gov24.max-calls-per-run:50}") int gov24SupportConditionsMaxCallsPerRun,
            @Value("${collect.detail.request-interval-ms:1000}") long detailRequestIntervalMs,
            @Value("${collect.detail.retry.max-attempts:3}") int detailRetryMaxAttempts,
            @Value("${collect.detail.retry.base-backoff-ms:1500}") long detailRetryBaseBackoffMs,
            @Value("${collect.detail.max-consecutive-rate-limit-hits:5}") int detailMaxConsecutiveRateLimitHits,
            @Value("${collect.youth.detail.request-interval-ms:500}") long youthDetailRequestIntervalMs,
            @Value("${collect.execution.lock-lease-minutes:15}") long lockLeaseMinutes,
            @Value("${collect.execution.lock-heartbeat-seconds:60}") long lockHeartbeatSeconds
    ) {
        this.listRequestIntervalMs = listRequestIntervalMs;
        this.listRetryMaxAttempts = listRetryMaxAttempts;
        this.listRetryBaseBackoffMs = listRetryBaseBackoffMs;
        this.listMaxConsecutiveRateLimitHits = listMaxConsecutiveRateLimitHits;
        this.listRateLimitCooldownMs = listRateLimitCooldownMs;
        this.listLocalRateLimitOpenCircuitMs = listLocalRateLimitOpenCircuitMs;
        this.listMaxItemsPerRun = listMaxItemsPerRun;
        this.gov24ListMaxItemsPerRun = gov24ListMaxItemsPerRun;
        this.detailCentralMaxCallsPerRun = detailCentralMaxCallsPerRun;
        this.detailLocalMaxCallsPerRun = detailLocalMaxCallsPerRun;
        this.gov24DetailMaxCallsPerRun = gov24DetailMaxCallsPerRun;
        this.gov24SupportConditionsMaxCallsPerRun = gov24SupportConditionsMaxCallsPerRun;
        this.detailRequestIntervalMs = detailRequestIntervalMs;
        this.detailRetryMaxAttempts = detailRetryMaxAttempts;
        this.detailRetryBaseBackoffMs = detailRetryBaseBackoffMs;
        this.detailMaxConsecutiveRateLimitHits = detailMaxConsecutiveRateLimitHits;
        this.youthDetailRequestIntervalMs = youthDetailRequestIntervalMs;
        this.lockLeaseMinutes = lockLeaseMinutes;
        this.lockHeartbeatSeconds = lockHeartbeatSeconds;
    }

    public List<ConfigEntrySpec> configEntriesFor(String laneKey) {
        return switch (laneKey) {
            case "YOUTH" -> List.of(
                    schedulerEntry(),
                    new ConfigEntrySpec("List pacing", millis(listRequestIntervalMs)),
                    new ConfigEntrySpec("Retry", retrySummary(listRetryMaxAttempts, listRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_CENTRAL" -> List.of(
                    schedulerEntry(),
                    new ConfigEntrySpec("Budget", itemsPerRun(listMaxItemsPerRun)),
                    new ConfigEntrySpec("List pacing", millis(listRequestIntervalMs)),
                    new ConfigEntrySpec("429 guard", rateLimitSummary(listMaxConsecutiveRateLimitHits, listRateLimitCooldownMs)),
                    new ConfigEntrySpec("Retry", retrySummary(listRetryMaxAttempts, listRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_LOCAL" -> List.of(
                    schedulerEntry(),
                    new ConfigEntrySpec("Budget", itemsPerRun(listMaxItemsPerRun)),
                    new ConfigEntrySpec("List pacing", millis(listRequestIntervalMs)),
                    new ConfigEntrySpec("429 guard", rateLimitSummary(listMaxConsecutiveRateLimitHits, listRateLimitCooldownMs)),
                    new ConfigEntrySpec("Open circuit", millis(listLocalRateLimitOpenCircuitMs)),
                    new ConfigEntrySpec("Retry", retrySummary(listRetryMaxAttempts, listRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_DETAIL" -> List.of(
                    schedulerEntry(),
                    new ConfigEntrySpec("Budget", "central " + callsPerRun(detailCentralMaxCallsPerRun)
                            + " / local " + callsPerRun(detailLocalMaxCallsPerRun)),
                    new ConfigEntrySpec("Detail pacing", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("429 abort", consecutiveHits(detailMaxConsecutiveRateLimitHits)),
                    new ConfigEntrySpec("Retry", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "GOV24" -> List.of(
                    new ConfigEntrySpec("Budget", itemsPerRun(gov24ListMaxItemsPerRun)),
                    new ConfigEntrySpec("List pacing", millis(listRequestIntervalMs)),
                    new ConfigEntrySpec("Retry", retrySummary(listRetryMaxAttempts, listRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "GOV24_DETAIL" -> List.of(
                    new ConfigEntrySpec("Budget", callsPerRun(gov24DetailMaxCallsPerRun)),
                    new ConfigEntrySpec("Detail pacing", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("Retry", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "GOV24_SUPPORT_CONDITIONS" -> List.of(
                    new ConfigEntrySpec("Budget", callsPerRun(gov24SupportConditionsMaxCallsPerRun)),
                    new ConfigEntrySpec("Detail pacing", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("Retry", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case CollectRuntimeLaneCatalog.YOUTH_DETAILS_LANE_KEY -> List.of(
                    new ConfigEntrySpec("Budget", "missing detail rows only"),
                    new ConfigEntrySpec("Detail pacing", millis(youthDetailRequestIntervalMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_DETAIL_GAP_FILL" -> List.of(
                    new ConfigEntrySpec("Budget", "operator supplied rounds/maxCallsPerRound"),
                    new ConfigEntrySpec("Per-source cap", "central " + callsPerRun(detailCentralMaxCallsPerRun)
                            + " / local " + callsPerRun(detailLocalMaxCallsPerRun)),
                    new ConfigEntrySpec("Detail pacing", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("429 abort", consecutiveHits(detailMaxConsecutiveRateLimitHits)),
                    new ConfigEntrySpec("Retry", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_DETAIL_REFRESH" -> List.of(
                    new ConfigEntrySpec("Budget", "central " + callsPerRun(detailCentralMaxCallsPerRun)
                            + " / local " + callsPerRun(detailLocalMaxCallsPerRun)),
                    new ConfigEntrySpec("Detail pacing", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("429 abort", consecutiveHits(detailMaxConsecutiveRateLimitHits)),
                    new ConfigEntrySpec("Retry", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            default -> List.of(lockGuardEntry());
        };
    }

    private ConfigEntrySpec schedulerEntry() {
        return new ConfigEntrySpec(
                "Scheduler",
                CollectBatchService.SCHEDULE_CRON + " @ " + CollectBatchService.SCHEDULE_ZONE
        );
    }

    private ConfigEntrySpec lockGuardEntry() {
        return new ConfigEntrySpec(
                "Lock guard",
                "lease " + lockLeaseMinutes + "m / heartbeat " + lockHeartbeatSeconds + "s"
        );
    }

    private String retrySummary(int maxAttempts, long baseBackoffMs) {
        return maxAttempts + " attempts / " + millis(baseBackoffMs) + " backoff";
    }

    private String rateLimitSummary(int maxHits, long cooldownMs) {
        return maxHits + "x 429 / cooldown " + millis(cooldownMs);
    }

    private String consecutiveHits(int maxHits) {
        return maxHits + " consecutive hits";
    }

    private String itemsPerRun(int count) {
        return "max " + count + " items/run";
    }

    private String callsPerRun(int count) {
        return "max " + count + " calls/run";
    }

    private String millis(long value) {
        return value + "ms";
    }

    public record ConfigEntrySpec(
            String label,
            String value
    ) {
    }
}
