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
    private final int rotationBokjiroDetailMaxCallsPerRun;
    private final int rotationBokjiroRefreshMaxCallsPerRun;
    private final int rotationGov24DetailMaxCallsPerRun;
    private final int rotationGov24SupportConditionsMaxCallsPerRun;
    private final int youthDetailMaxCallsPerRun;
    private final int diffForceNewCountThreshold;
    private final double diffForceNewShareThreshold;
    private final int diffForceChangedCountThreshold;
    private final double diffForceChangedShareThreshold;
    private final int diffForceMaxCandidatesPerRun;
    private final double diffCountDropShareThreshold;
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
            @Value("${collect.list.rotation.bokjiro-detail-max-calls-per-run:100}") int rotationBokjiroDetailMaxCallsPerRun,
            @Value("${collect.list.rotation.bokjiro-refresh-max-calls-per-run:50}") int rotationBokjiroRefreshMaxCallsPerRun,
            @Value("${collect.list.rotation.gov24-detail-max-calls-per-run:50}") int rotationGov24DetailMaxCallsPerRun,
            @Value("${collect.list.rotation.gov24-support-conditions-max-calls-per-run:50}") int rotationGov24SupportConditionsMaxCallsPerRun,
            @Value("${collect.youth.detail.max-calls-per-run:50}") int youthDetailMaxCallsPerRun,
            @Value("${collect.list.diff.force-detail.new-count-threshold:30}") int diffForceNewCountThreshold,
            @Value("${collect.list.diff.force-detail.new-share-threshold:0.02}") double diffForceNewShareThreshold,
            @Value("${collect.list.diff.force-detail.changed-count-threshold:100}") int diffForceChangedCountThreshold,
            @Value("${collect.list.diff.force-detail.changed-share-threshold:0.05}") double diffForceChangedShareThreshold,
            @Value("${collect.list.diff.force-detail.max-candidates-per-run:50}") int diffForceMaxCandidatesPerRun,
            @Value("${collect.list.diff.guard.count-drop-share-threshold:0.15}") double diffCountDropShareThreshold,
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
        this.rotationBokjiroDetailMaxCallsPerRun = rotationBokjiroDetailMaxCallsPerRun;
        this.rotationBokjiroRefreshMaxCallsPerRun = rotationBokjiroRefreshMaxCallsPerRun;
        this.rotationGov24DetailMaxCallsPerRun = rotationGov24DetailMaxCallsPerRun;
        this.rotationGov24SupportConditionsMaxCallsPerRun = rotationGov24SupportConditionsMaxCallsPerRun;
        this.youthDetailMaxCallsPerRun = youthDetailMaxCallsPerRun;
        this.diffForceNewCountThreshold = diffForceNewCountThreshold;
        this.diffForceNewShareThreshold = diffForceNewShareThreshold;
        this.diffForceChangedCountThreshold = diffForceChangedCountThreshold;
        this.diffForceChangedShareThreshold = diffForceChangedShareThreshold;
        this.diffForceMaxCandidatesPerRun = diffForceMaxCandidatesPerRun;
        this.diffCountDropShareThreshold = diffCountDropShareThreshold;
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
                    new ConfigEntrySpec("목록 요청 간격", millis(listRequestIntervalMs)),
                    diffForceEntry(),
                    new ConfigEntrySpec("재시도", retrySummary(listRetryMaxAttempts, listRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_CENTRAL" -> List.of(
                    schedulerEntry(),
                    new ConfigEntrySpec("실행 한도", itemsPerRun(listMaxItemsPerRun)),
                    new ConfigEntrySpec("목록 요청 간격", millis(listRequestIntervalMs)),
                    diffForceEntry(),
                    new ConfigEntrySpec("요청 제한 보호", rateLimitSummary(listMaxConsecutiveRateLimitHits, listRateLimitCooldownMs)),
                    new ConfigEntrySpec("재시도", retrySummary(listRetryMaxAttempts, listRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_LOCAL" -> List.of(
                    schedulerEntry(),
                    new ConfigEntrySpec("실행 한도", itemsPerRun(listMaxItemsPerRun)),
                    new ConfigEntrySpec("목록 요청 간격", millis(listRequestIntervalMs)),
                    diffForceEntry(),
                    new ConfigEntrySpec("요청 제한 보호", rateLimitSummary(listMaxConsecutiveRateLimitHits, listRateLimitCooldownMs)),
                    new ConfigEntrySpec("회로 열림 유지 시간", millis(listLocalRateLimitOpenCircuitMs)),
                    new ConfigEntrySpec("재시도", retrySummary(listRetryMaxAttempts, listRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_DETAIL" -> List.of(
                    rotationEntry("월요일"),
                    new ConfigEntrySpec("rotation 실행 한도", callsPerRun(rotationBokjiroDetailMaxCallsPerRun)),
                    new ConfigEntrySpec("source default 한도", "중앙 " + callsPerRun(detailCentralMaxCallsPerRun)
                            + " / 지자체 " + callsPerRun(detailLocalMaxCallsPerRun)),
                    new ConfigEntrySpec("상세 요청 간격", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("요청 제한 중단 기준", consecutiveHits(detailMaxConsecutiveRateLimitHits)),
                    new ConfigEntrySpec("재시도", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "GOV24" -> List.of(
                    schedulerEntry(),
                    new ConfigEntrySpec("실행 한도", itemsPerRun(gov24ListMaxItemsPerRun)),
                    new ConfigEntrySpec("목록 요청 간격", millis(listRequestIntervalMs)),
                    diffForceEntry(),
                    new ConfigEntrySpec("재시도", retrySummary(listRetryMaxAttempts, listRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "GOV24_DETAIL" -> List.of(
                    rotationEntry("화요일"),
                    new ConfigEntrySpec("rotation 실행 한도", callsPerRun(rotationGov24DetailMaxCallsPerRun)),
                    new ConfigEntrySpec("manual/source default 한도", callsPerRun(gov24DetailMaxCallsPerRun)),
                    new ConfigEntrySpec("상세 요청 간격", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("재시도", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "GOV24_SUPPORT_CONDITIONS" -> List.of(
                    rotationEntry("수요일"),
                    new ConfigEntrySpec("rotation 실행 한도", callsPerRun(rotationGov24SupportConditionsMaxCallsPerRun)),
                    new ConfigEntrySpec("manual/source default 한도", callsPerRun(gov24SupportConditionsMaxCallsPerRun)),
                    new ConfigEntrySpec("상세 요청 간격", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("재시도", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case CollectRuntimeLaneCatalog.YOUTH_DETAILS_LANE_KEY -> List.of(
                    rotationEntry("금요일"),
                    new ConfigEntrySpec("실행 한도", callsPerRun(youthDetailMaxCallsPerRun) + " / 상세 정보가 없는 항목 우선"),
                    new ConfigEntrySpec("상세 요청 간격", millis(youthDetailRequestIntervalMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_DETAIL_GAP_FILL" -> List.of(
                    new ConfigEntrySpec("실행 한도", "관리자가 지정한 회차와 회차별 호출 수"),
                    new ConfigEntrySpec("출처별 한도", "중앙 " + callsPerRun(detailCentralMaxCallsPerRun)
                            + " / 지자체 " + callsPerRun(detailLocalMaxCallsPerRun)),
                    new ConfigEntrySpec("상세 요청 간격", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("요청 제한 중단 기준", consecutiveHits(detailMaxConsecutiveRateLimitHits)),
                    new ConfigEntrySpec("재시도", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            case "BOKJIRO_DETAIL_REFRESH" -> List.of(
                    rotationEntry("목요일"),
                    new ConfigEntrySpec("rotation 실행 한도", callsPerRun(rotationBokjiroRefreshMaxCallsPerRun)),
                    new ConfigEntrySpec("source default 한도", "중앙 " + callsPerRun(detailCentralMaxCallsPerRun)
                            + " / 지자체 " + callsPerRun(detailLocalMaxCallsPerRun)),
                    new ConfigEntrySpec("상세 요청 간격", millis(detailRequestIntervalMs)),
                    new ConfigEntrySpec("요청 제한 중단 기준", consecutiveHits(detailMaxConsecutiveRateLimitHits)),
                    new ConfigEntrySpec("재시도", retrySummary(detailRetryMaxAttempts, detailRetryBaseBackoffMs)),
                    lockGuardEntry()
            );
            default -> List.of(lockGuardEntry());
        };
    }

    private ConfigEntrySpec schedulerEntry() {
        return new ConfigEntrySpec(
                "자동 실행 일정",
                CollectBatchService.SCHEDULE_CRON + " @ " + CollectBatchService.SCHEDULE_ZONE
        );
    }

    private ConfigEntrySpec rotationEntry(String dayLabel) {
        return new ConfigEntrySpec(
                "rotation 일정",
                dayLabel + " / diff forced candidate 우선"
        );
    }

    private ConfigEntrySpec diffForceEntry() {
        return new ConfigEntrySpec(
                "list diff forced detail",
                "신규 " + diffForceNewCountThreshold + "건 또는 " + percent(diffForceNewShareThreshold)
                        + " / 변경 " + diffForceChangedCountThreshold + "건 또는 " + percent(diffForceChangedShareThreshold)
                        + " / 후보 최대 " + diffForceMaxCandidatesPerRun
                        + " / 급감 guard " + percent(diffCountDropShareThreshold)
        );
    }

    private ConfigEntrySpec lockGuardEntry() {
        return new ConfigEntrySpec(
                "중복 실행 방지",
                "잠금 " + lockLeaseMinutes + "분 / 상태 확인 " + lockHeartbeatSeconds + "초"
        );
    }

    private String retrySummary(int maxAttempts, long baseBackoffMs) {
        return maxAttempts + "회 시도 / " + millis(baseBackoffMs) + " 대기";
    }

    private String rateLimitSummary(int maxHits, long cooldownMs) {
        return maxHits + "회 요청 제한 / " + millis(cooldownMs) + " 후 재개";
    }

    private String consecutiveHits(int maxHits) {
        return maxHits + "회 연속";
    }

    private String itemsPerRun(int count) {
        return "최대 " + count + "건/회";
    }

    private String callsPerRun(int count) {
        return "최대 " + count + "회 호출/회";
    }

    private String millis(long value) {
        return value + "ms";
    }

    private String percent(double value) {
        return (value * 100.0) + "%";
    }

    public record ConfigEntrySpec(
            String label,
            String value
    ) {
    }
}
