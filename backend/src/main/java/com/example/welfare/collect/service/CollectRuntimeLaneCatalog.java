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
                        "핵심 청년 정책 목록입니다. 매일 밤 기준 데이터를 갱신하기 위해 자동 수집합니다."),
                scheduled(CollectSource.BOKJIRO_CENTRAL, "SNAPSHOT", "STANDARD",
                        "복지로 중앙 정책 목록입니다. 상세 수집과 분리해 기본 목록 기준으로 유지합니다."),
                scheduled(CollectSource.BOKJIRO_LOCAL, "SNAPSHOT", "STANDARD",
                        "복지로 지자체 정책 목록입니다. 요청 제한과 회로 열림 상태를 모니터링하며 매일 밤 갱신합니다."),
                scheduled(CollectSource.BOKJIRO_DETAIL, "DETAIL", "BUDGETED",
                        "복지로 상세 정보입니다. 호출 한도와 요청 간격을 둬서 안전하게 자동 수집합니다."),
                manual(CollectSource.GOV24, "SNAPSHOT", "ON_DEMAND",
                        "정부24 목록은 자동 수집에서 제외하고 필요할 때 수동 실행합니다. 수동 실행 시 상세/지원조건 수집이 이어집니다."),
                manual(CollectSource.GOV24_DETAIL, "DETAIL", "BUDGETED",
                        "정부24 상세 정보입니다. 호출 한도를 두고 수동으로만 실행해 외부 API 사용량을 제어합니다."),
                manual(CollectSource.GOV24_SUPPORT_CONDITIONS, "DETAIL", "BUDGETED",
                        "정부24 지원조건 상세 정보입니다. 목록 수집과 분리해 수동 실행과 호출 한도로 운영합니다."),
                new CollectLaneSpec(
                        YOUTH_DETAILS_LANE_KEY,
                        "온통청년 상세 보강",
                        "MANUAL",
                        "ENRICHMENT",
                        YOUTH_DETAILS_TRIGGER_PATH,
                        null,
                        "BUDGETED",
                        "온통청년 상세 링크 같은 보강 정보입니다. 목록 수집과 분리해 천천히 수동 실행합니다."
                ),
                manual(CollectSource.BOKJIRO_DETAIL_GAP_FILL, "MAINTENANCE", "BUDGETED",
                        "복지로 상세 정보 누락분을 메우는 유지보수 작업입니다. 누락이 남을 때만 수동 실행합니다."),
                manual(CollectSource.BOKJIRO_DETAIL_REFRESH, "MAINTENANCE", "BUDGETED",
                        "기존 복지로 상세 정보를 다시 읽는 유지보수 작업입니다. 데이터 변경 확인이 필요할 때 수동 실행합니다.")
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
