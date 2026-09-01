import {
  ATTENTION_PROMOTION_KEY_RANK,
  ATTENTION_PROMOTION_RANK,
  INFO_TEXT,
  INK3,
  SUCCESS_BG,
  SUCCESS_BORDER,
  SUCCESS_TEXT,
  WARNING_BG,
  WARNING_BORDER,
  WARNING_TEXT,
  formatAttentionActionLabel,
  formatAttentionSource,
} from "../components/adminDashboard/AdminDashboardUiTokens";
import {
  formatAdminOperationalText,
  formatNumber,
} from "./adminDashboardDisplay";

export function useAdminDashboardTopDerived({
  summaryData,
  breakdowns,
  collectFailures,
  searchFailures,
  standardCodeCoverage,
  attentionFeed,
  wrapperObservation,
  failedSectionCount,
}) {
  const latestGeneratedAt = summaryData?.generatedAt ?? breakdowns?.generatedAt ?? null;
  const openCircuitCount = collectFailures?.circuitStatuses?.filter((item) => item.open).length ?? 0;
  const retryGroupCount = searchFailures?.retryGroups?.length ?? 0;
  const recoveredGroupCount = searchFailures?.recoveredSearchGroups?.length ?? 0;
  const wrapperMissingAllStandardCodes =
    wrapperObservation?.currentPriorityUsersMissingAllStandardCodes
    ?? wrapperObservation?.activeBaselineUsersMissingAllStandardCodes
    ?? standardCodeCoverage?.usersMissingAllStandardCodes
    ?? 0;
  const wrapperRecommendationObservationStatus =
    wrapperObservation?.currentPriorityRecommendationObservationStatus
    || wrapperObservation?.activeBaselineRecommendationObservationStatus
    || "—";
  const wrapperActiveBaselineReuseLabel = wrapperObservation?.currentPriorityAvailable
    ? (wrapperObservation.currentPriorityActiveBaselineReused ? "재사용" : "직접 실행")
    : "—";
  const wrapperAttentionFeedStatus =
    wrapperObservation?.currentPriorityAttentionFeedStatus
    || wrapperObservation?.activeBaselineAttentionFeedStatus
    || "—";
  const wrapperAttentionFeedItemCount =
    wrapperObservation?.currentPriorityAttentionFeedItemCount
    ?? wrapperObservation?.activeBaselineAttentionFeedItemCount
    ?? attentionFeed?.itemCount
    ?? 0;
  const wrapperAttentionFeedPrimaryTitle =
    formatAdminOperationalText(
      wrapperObservation?.currentPriorityAttentionFeedItemTitles
      || wrapperObservation?.activeBaselineAttentionFeedItemTitles
      || attentionFeed?.items?.[0]?.title,
      "대표 항목 없음"
    );
  const wrapperAttentionFeedPrimaryKey =
    wrapperObservation?.currentPriorityAttentionFeedItemKeys
    || wrapperObservation?.activeBaselineAttentionFeedItemKeys
    || attentionFeed?.items?.[0]?.key
    || "";
  const wrapperAttentionFeedPrimarySeverity =
    (wrapperObservation?.currentPriorityAttentionFeedWarningItemCount ?? wrapperObservation?.activeBaselineAttentionFeedWarningItemCount ?? 0) > 0
      ? "warning"
      : (attentionFeed?.items?.[0]?.severity || (wrapperAttentionFeedItemCount > 0 ? "warning" : "success"));
  const wrapperAttentionFeedPrimarySource = formatAttentionSource(
    wrapperAttentionFeedPrimaryKey || attentionFeed?.items?.[0]?.source
  );
  const wrapperAttentionFeedPrimaryTargetId = (
    attentionFeed?.items?.find((item) => item.key === wrapperAttentionFeedPrimaryKey)?.targetId
    || attentionFeed?.items?.find((item) => item.title === wrapperAttentionFeedPrimaryTitle)?.targetId
    || "admin-attention-queue"
  );
  const wrapperAttentionFeedPrimaryActionLabel = formatAttentionActionLabel(wrapperAttentionFeedPrimarySource);
  const wrapperAttentionFeedTone = wrapperAttentionFeedItemCount > 0
    ? { label: "주시", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT }
    : { label: "양호", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT };
  const wrapperMissingStandardCodeTone = wrapperMissingAllStandardCodes > 0
    ? { label: "정리 필요", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT }
    : { label: "양호", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT };
  const wrapperMissingDelta = wrapperObservation?.currentPriorityUsersMissingAllStandardCodesDelta ?? 0;
  const wrapperMissingDeltaLabel = formatAdminOperationalText(
    wrapperObservation?.currentPriorityUsersMissingAllStandardCodesDeltaLabel
    || (
      wrapperObservation?.currentPriorityPreviousAvailable
        ? (
            wrapperMissingDelta > 0
              ? `${formatNumber(wrapperMissingDelta)} 증가`
              : wrapperMissingDelta < 0
                ? `${formatNumber(Math.abs(wrapperMissingDelta))} 감소`
                : "변화 없음"
          )
        : "이전값 없음"
    )
  );
  const wrapperMissingDeltaColor = wrapperObservation?.currentPriorityPreviousAvailable
    ? (
        wrapperMissingDelta > 0
          ? WARNING_TEXT
          : wrapperMissingDelta < 0
            ? SUCCESS_TEXT
            : INK3
      )
    : INK3;
  const wrapperObservationChangeLabel = formatAdminOperationalText(
    wrapperObservation?.currentPriorityRecommendationObservationStatusTransitionLabel
    || (
      wrapperObservation?.currentPriorityPreviousAvailable
        ? (
            wrapperObservation?.currentPriorityRecommendationObservationStatusChanged
              ? `${wrapperObservation.currentPriorityPreviousRecommendationObservationStatus || "—"} -> ${wrapperRecommendationObservationStatus}`
              : "변화 없음"
          )
        : "이전값 없음"
    )
  );
  const wrapperObservationChangeColor = wrapperObservation?.currentPriorityPreviousAvailable
    ? (
        wrapperObservation?.currentPriorityRecommendationObservationStatusChanged
          ? INFO_TEXT
          : INK3
      )
    : INK3;
  const wrapperSnapshotAlert = wrapperObservation?.promotedAlert
    ? {
        severity: wrapperObservation.promotedAlert.severity,
        title: formatAdminOperationalText(wrapperObservation.promotedAlert.title),
        message: formatAdminOperationalText(wrapperObservation.promotedAlert.message),
      }
    : null;
  const localAttentionQueueItems = [
    failedSectionCount > 0 ? {
      key: "section-failures",
      severity: "warning",
      title: "대시보드 섹션 재시도 필요",
      message: `${formatNumber(failedSectionCount)}개 섹션이 실패했습니다. 실패 카드부터 다시 불러오세요.`,
      nextAction: "실패한 섹션의 다시 시도를 먼저 누르고, 같은 API만 반복 실패하면 해당 섹션 runbook과 서버 로그를 확인합니다.",
      targetId: "admin-attention-queue",
    } : null,
  ].filter(Boolean);
  const attentionQueueItems = [
    ...localAttentionQueueItems,
    ...(attentionFeed?.items ?? []),
  ].map((item) => ({
    ...item,
    title: formatAdminOperationalText(item.title),
    message: formatAdminOperationalText(item.message),
    nextAction: formatAdminOperationalText(item.nextAction, ""),
  }));
  const promotedAttentionItems = [...attentionQueueItems]
    .sort((left, right) => {
      const leftRank = ATTENTION_PROMOTION_RANK[left.severity] ?? 99;
      const rightRank = ATTENTION_PROMOTION_RANK[right.severity] ?? 99;
      if (leftRank !== rightRank) {
        return leftRank - rightRank;
      }
      const leftKeyRank = ATTENTION_PROMOTION_KEY_RANK[left.key] ?? 99;
      const rightKeyRank = ATTENTION_PROMOTION_KEY_RANK[right.key] ?? 99;
      return leftKeyRank - rightKeyRank;
    })
    .slice(0, 3);

  return {
    latestGeneratedAt,
    openCircuitCount,
    retryGroupCount,
    recoveredGroupCount,
    wrapperMissingAllStandardCodes,
    wrapperRecommendationObservationStatus,
    wrapperActiveBaselineReuseLabel,
    wrapperAttentionFeedStatus,
    wrapperAttentionFeedItemCount,
    wrapperAttentionFeedPrimaryTitle,
    wrapperAttentionFeedPrimaryKey,
    wrapperAttentionFeedPrimarySeverity,
    wrapperAttentionFeedPrimarySource,
    wrapperAttentionFeedPrimaryTargetId,
    wrapperAttentionFeedPrimaryActionLabel,
    wrapperAttentionFeedTone,
    wrapperMissingStandardCodeTone,
    wrapperMissingDeltaLabel,
    wrapperMissingDeltaColor,
    wrapperObservationChangeLabel,
    wrapperObservationChangeColor,
    wrapperSnapshotAlert,
    localAttentionQueueItems,
    attentionQueueItems,
    promotedAttentionItems,
  };
}
