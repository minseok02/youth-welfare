// Attribute names shared by the dashboard UI and Playwright tests.
// Keep these centralized so selector changes do not require scattered updates.
export const ADMIN_DASHBOARD_TEST_ATTRS = {
  attentionActionKey: "data-attention-action-key",
  attentionKey: "data-attention-key",
  attentionPrimaryActionKey: "data-attention-primary-action-key",
  attentionPrimaryKey: "data-attention-primary-key",
  adminActionKey: "data-admin-action-key",
  adminCardKey: "data-admin-card-key",
  adminListKey: "data-admin-list-key",
  jumpActive: "data-jump-active",
  jumpActiveTone: "data-jump-active-tone",
  jumpFocusTarget: "data-jump-focus-target",
  jumpFocusTone: "data-jump-focus-tone",
};

// Semantic identifiers for attention items promoted from backend feeds.
export const ADMIN_DASHBOARD_ATTENTION_KEYS = {
  collectDrift: "collect-drift",
  standardCodeBacklog: "standard-code-backlog",
  wrapperWarning: "wrapper-warning",
};

// Stable card-level keys used when a quick jump should land on a specific summary card.
export const ADMIN_DASHBOARD_CARD_KEYS = {
  currentPriorityMissingStandardCodes: "current-priority-missing-standard-codes",
  matchedRowCount: "matched-row-count",
  top1LeaderSignal: "top1-leader-signal",
  welfareScenarioCount: "welfare-scenario-count",
};

// Stable action-level keys used when tests need to click card actions without relying on button text order.
export const ADMIN_DASHBOARD_ACTION_KEYS = {
  collectCircuitView: "collect-circuit-view",
  collectFailureSampleView: "collect-failure-sample-view",
  collectPartialView: "collect-partial-view",
  quickJumpAttentionQueue: "quick-jump-attention-queue",
  quickJumpCollectTriage: "quick-jump-collect-triage",
  quickJumpRecommendationBreakdowns: "quick-jump-recommendation-breakdowns",
  quickJumpRecommendationOverview: "quick-jump-recommendation-overview",
  quickJumpReferenceCodebooks: "quick-jump-reference-codebooks",
  quickJumpSearchTriage: "quick-jump-search-triage",
  quickJumpStandardCodeCoverage: "quick-jump-standard-code-coverage",
  quickJumpStandardCodeEffect: "quick-jump-standard-code-effect",
  quickJumpWrapperObservation: "quick-jump-wrapper-observation",
  searchRecoveredView: "search-recovered-view",
  searchWarningView: "search-warning-view",
};

// Stable list-level keys used when a jump should highlight an enclosing list card first.
export const ADMIN_DASHBOARD_LIST_KEYS = {
  collectCircuitStatuses: "collect-circuit-statuses",
  collectJobBreakdowns: "collect-job-breakdowns",
  collectRecentFailureSamples: "collect-recent-failure-samples",
  searchRecoveredGroups: "search-recovered-groups",
  searchRecentZeroResultSamples: "search-recent-zero-result-samples",
  topRepeatedServices: "top-repeated-services",
};

// Focus keys point to the exact row/item that should flash after the parent section/card is active.
export const ADMIN_DASHBOARD_FOCUS_KEYS = {
  collectCircuit: "collect-circuit",
  collectPartial: "collect-partial",
  default: "default",
  searchRecovered: "search-recovered",
  searchWarning: "search-warning",
};

// Presets tune jump timing by entry type.
// attention: strongest guidance from promoted alerts
// quickJump: top-level dashboard shortcut buttons
// metricAction: within-section action buttons on individual cards
export const ADMIN_DASHBOARD_JUMP_PRESETS = {
  attention: {
    sectionDuration: 30000,
    containerDuration: 30000,
    focusDuration: 30000,
  },
  quickJump: {
    sectionDuration: 30000,
    containerDuration: 30000,
    focusDuration: 30000,
  },
  metricAction: {
    sectionDuration: 30000,
    containerDuration: 30000,
    focusDuration: 30000,
  },
};

export const ADMIN_DASHBOARD_DEFAULT_JUMP_PRESET = "quickJump";

export function buildDashboardDataAttr(attrName, value) {
  return { [attrName]: value };
}
