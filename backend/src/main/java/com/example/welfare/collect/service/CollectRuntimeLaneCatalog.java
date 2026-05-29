package com.example.welfare.collect.service;

import java.util.List;

public final class CollectRuntimeLaneCatalog {

    public static final String YOUTH_DETAILS_LANE_KEY = "YOUTH_DETAILS";
    public static final String YOUTH_DETAILS_LOCK_NAME = "collect-youth-details";
    public static final String YOUTH_DETAILS_TRIGGER_PATH = "/api/admin/collect/youth-details";
    public static final String INVERTED_AGE_BACKFILL_LOCK_NAME = "collect-inverted-age-backfill";
    public static final String INVERTED_AGE_BACKFILL_TRIGGER_PATH = "/api/admin/collect/inverted-age-backfill";

    private CollectRuntimeLaneCatalog() {
    }

    public static List<CollectLaneSpec> currentLanes() {
        return List.of(
                scheduled(CollectSource.YOUTH, "SNAPSHOT", "HEAVY",
                        "핵심 청년 snapshot lane이다. nightly 기준선 유지 때문에 자동수집에 포함한다."),
                scheduled(CollectSource.BOKJIRO_CENTRAL, "SNAPSHOT", "STANDARD",
                        "복지로 중앙 목록 snapshot lane이다. detail/backfill과 분리된 기본 기준선으로 유지한다."),
                scheduled(CollectSource.BOKJIRO_LOCAL, "SNAPSHOT", "STANDARD",
                        "복지로 지자체 목록 snapshot lane이다. local 429/open-circuit triage 대상이라 nightly 기준선에 포함한다."),
                scheduled(CollectSource.BOKJIRO_DETAIL, "DETAIL", "BUDGETED",
                        "복지로 detail nightly lane이다. quota/runtime 보호를 위해 call budget과 pacing guard를 둔 채 자동수집한다."),
                manual(CollectSource.GOV24, "SNAPSHOT", "ON_DEMAND",
                        "nightly에서는 제외한다. 수동 실행 시 Gov24 list 뒤에 detail/supportConditions를 연쇄 실행한다."),
                manual(CollectSource.GOV24_DETAIL, "DETAIL", "BUDGETED",
                        "Gov24 detail 확장 lane이다. maxCallsPerRun 또는 sourceId override로만 수동 실행해 quota/runtime를 제어한다."),
                manual(CollectSource.GOV24_SUPPORT_CONDITIONS, "DETAIL", "BUDGETED",
                        "Gov24 지원조건 확장 lane이다. list snapshot과 분리해 수동/budgeted로만 운영한다."),
                new CollectLaneSpec(
                        YOUTH_DETAILS_LANE_KEY,
                        "온통청년 DETAIL",
                        "MANUAL",
                        "ENRICHMENT",
                        YOUTH_DETAILS_TRIGGER_PATH,
                        null,
                        "BUDGETED",
                        "refUrlAddr1/2 같은 detail 보강 lane이다. 500ms pacing으로 느리게 돌리며 목록 snapshot과 분리 운영한다."
                ),
                manual(CollectSource.BOKJIRO_DETAIL_GAP_FILL, "MAINTENANCE", "BUDGETED",
                        "복지로 detail backlog를 메우는 one-off maintenance lane이다. coverage gap이 남을 때만 수동 실행한다."),
                manual(CollectSource.BOKJIRO_DETAIL_REFRESH, "MAINTENANCE", "BUDGETED",
                        "기존 복지로 detail row를 다시 읽는 rerun lane이다. nightly 기본 lane이 아니라 drift/refresh 목적의 수동 경로다.")
        );
    }

    private static CollectLaneSpec scheduled(CollectSource source,
                                             String laneType,
                                             String resourceProfile,
                                             String governanceReason) {
        return new CollectLaneSpec(
                source.jobName(),
                source.triggerLabel(),
                "SCHEDULED",
                laneType,
                triggerPath(source),
                CollectBatchService.SCHEDULE_LABEL,
                resourceProfile,
                governanceReason
        );
    }

    private static CollectLaneSpec manual(CollectSource source,
                                          String laneType,
                                          String resourceProfile,
                                          String governanceReason) {
        return new CollectLaneSpec(
                source.jobName(),
                source.triggerLabel(),
                "MANUAL",
                laneType,
                triggerPath(source),
                null,
                resourceProfile,
                governanceReason
        );
    }

    private static String triggerPath(CollectSource source) {
        return "/api/admin/collect/" + source.pathKey();
    }

    public record CollectLaneSpec(
            String laneKey,
            String label,
            String executionMode,
            String laneType,
            String triggerPath,
            String scheduleLabel,
            String resourceProfile,
            String governanceReason
    ) {
    }
}
