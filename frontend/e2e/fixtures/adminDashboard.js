const generatedAt = "2026-05-29T01:23:45Z";

export function buildAdminDashboardApiPayload(data) {
  return {
    success: true,
    data,
  };
}

export const adminDashboardFixtures = {
  summary: {
    generatedAt,
    recommendation: {
      recommendationReviewGate: "READY_BALANCED_LOGIC_REVIEW",
      realUserTrafficGateInWindow: "READY_REAL_USER_TRAFFIC",
      latestBatchConcentration: {
        top1LeaderSignalSummary: "READY_BALANCED_LOGIC_REVIEW",
        top1LeaderTitle: "청년 취업 지원 점검 정책",
        top1LeaderSource: "BOKJIRO_LOCAL",
        top1LeaderCategory: "일자리",
        top1LeaderSharePct: 62.5,
        latestBatchUsers: 8,
        latestBatchRows: 14,
        latestBatchDistinctServices: 5,
        realUserCohortGate: "READY_REAL_USER_COHORT",
        signalQuality: "READY_BALANCED_LOGIC_REVIEW",
        top1LeaderServiceId: 101,
        top1LeaderUserMix: {
          exampleUsers: 1,
          boundedLocalUsers: 2,
          localRealNonExampleSeedUsers: 1,
          realUserUsers: 4,
        },
        concentrationReadiness: "READY_BALANCED_LOGIC_REVIEW",
      },
    },
    collect: {
      failedJobsLast24h: 1,
      windowDays: 14,
      runningJobs: 0,
      latestFailuresInWindow: [
        {
          jobName: "gov24-detail-sync",
          errorCode: "HTTP_503",
          status: "FAILED",
          startedAt: generatedAt,
          failedCount: 2,
        },
      ],
    },
    notification: {
      failedInWindow: 0,
      sentInWindow: 12,
      failedLast24h: 0,
    },
    search: {
      zeroResultSearchesInWindow: 3,
      searchesInWindow: 21,
      uniqueFingerprintsInWindow: 4,
      zeroResultKeywordsInWindow: [
        {
          keyword: "월세",
          searchCount: 2,
        },
      ],
    },
    userPiiSync: {
      failedCount: 0,
      pendingCount: 0,
      latestSyncedAt: generatedAt,
    },
  },
  breakdowns: {
    generatedAt,
    topRepeatedServices: [
      {
        serviceId: 101,
        title: "청년 취업 지원 점검 정책",
        sourceType: "BOKJIRO_LOCAL",
        category: "일자리",
        rowCount: 6,
        userMix: {
          exampleUsers: 1,
          boundedLocalUsers: 1,
          localRealNonExampleSeedUsers: 1,
          realUserUsers: 3,
        },
      },
    ],
    top1Services: [
      {
        serviceId: 102,
        title: "청년 생활안정 지원 패키지",
        sourceType: "YOUTH",
        category: "금융·생활지원",
        usersAsTop1: 4,
        userMix: {
          exampleUsers: 0,
          boundedLocalUsers: 1,
          localRealNonExampleSeedUsers: 1,
          realUserUsers: 2,
        },
      },
    ],
    youthOfficialFacetGroups: [
      {
        facetKey: "lifeStage",
        label: "생애주기",
        buckets: [
          {
            label: "청년",
            rowCount: 8,
            distinctServices: 4,
          },
        ],
      },
    ],
    gov24FacetGroups: [
      {
        facetKey: "supportType",
        label: "지원유형",
        buckets: [
          {
            label: "취업지원",
            rowCount: 5,
            distinctServices: 2,
          },
        ],
      },
    ],
  },
  collectFailures: {
    windowDays: 14,
    failedJobsInWindow: 2,
    partialSuccessJobsInWindow: 1,
    circuitStatuses: [
      {
        circuitKey: "gov24-detail",
        open: false,
        openUntil: generatedAt,
        remainingMs: 0,
      },
    ],
    collectSourceLanes: [
      {
        laneKey: "SCHEDULED",
        label: "정부24 야간 수집",
        triggerPath: "/api/collect/gov24/nightly",
        governanceReason: "야간 자동수집 기준선",
        latestRun: {
          status: "SUCCESS",
          startedAt: generatedAt,
          requestedCount: 10,
          savedCount: 9,
          skippedCount: 1,
          failedCount: 0,
        },
        configEntries: [
          {
            label: "batchSize",
            value: "10",
          },
        ],
        scheduleLabel: "매일 02:00",
        executionMode: "SCHEDULED",
        laneType: "SNAPSHOT",
        resourceProfile: "STANDARD",
      },
    ],
    jobBreakdowns: [
      {
        jobName: "gov24-detail-sync",
        latestStartedAt: generatedAt,
        failedCount: 2,
        partialSuccessCount: 1,
      },
    ],
    errorCodeBreakdowns: [
      {
        errorCode: "HTTP_503",
        failedCount: 2,
      },
    ],
    currentJobStreaks: [
      {
        jobName: "gov24-detail-sync",
        streakStatus: "FAILED",
        latestStartedAt: generatedAt,
        streakCount: 2,
      },
    ],
    recentSamples: [
      {
        jobName: "gov24-detail-sync",
        startedAt: generatedAt,
        errorCode: "HTTP_503",
        status: "FAILED",
        requestedCount: 10,
        savedCount: 7,
        failedCount: 3,
        errorMessage: "temporary upstream issue",
      },
    ],
  },
  searchFailures: {
    windowDays: 14,
    zeroResultSearchesInWindow: 3,
    retryGroups: [
      {
        actorType: "USER",
        actorKey: "user-1",
        keyword: "월세",
        latestSearchedAt: generatedAt,
        firstSearchedAt: "2026-05-28T23:00:00Z",
        retryCount: 2,
      },
    ],
    recoveredSearchGroups: [
      {
        actorType: "USER",
        actorKey: "user-2",
        keyword: "취업",
        latestRecoveredAt: generatedAt,
        zeroResultCount: 1,
        recoveredResultCount: 2,
      },
    ],
    recentSamples: [
      {
        keyword: "월세",
        searchedAt: generatedAt,
        sido: "서울특별시",
        sgg: "중구",
        category: "주거",
        sourceType: "BOKJIRO_LOCAL",
        statusFilter: "ACTIVE_ONLY",
      },
    ],
    zeroResultRegions: [
      {
        sido: "서울특별시",
        sgg: "중구",
        searchCount: 2,
      },
    ],
    zeroResultFilterPatterns: [
      {
        statusFilter: "ACTIVE_ONLY",
        category: "주거",
        sourceType: "BOKJIRO_LOCAL",
        onlineApply: true,
        includeClosed: false,
        sortKey: "RELEVANCE",
        searchCount: 2,
      },
    ],
  },
};
