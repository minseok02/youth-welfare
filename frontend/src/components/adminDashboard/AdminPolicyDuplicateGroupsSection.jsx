import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import {
  CompactListCard,
  MetricCard,
  SectionErrorCard,
  SectionLoadingCard,
} from "./AdminDashboardUi";
import {
  ACCENT,
  ADMIN_QUEUE_STATUS_OPTIONS,
  INFO_BG,
  INFO_BORDER,
  INFO_TEXT,
  INK,
  INK2,
  INK3,
  PANEL_BG,
  PANEL_LINE,
  WARNING_BG,
  WARNING_BORDER,
  WARNING_TEXT,
} from "./AdminDashboardUiTokens";
import {
  formatAdminReviewNoteSuffix,
  formatDateTime,
  formatMaskedUserKey,
  formatNumber,
  formatPolicyDuplicateReviewClass,
  formatSourceType,
} from "../../lib/adminDashboardDisplay";

export default function AdminPolicyDuplicateGroupsSection({
  filters,
  queries,
  data,
  state,
  actions,
}) {
  const {
    policyDuplicateStatusFilter,
    onPolicyDuplicateStatusFilterChange,
  } = filters;
  const {
    policyDuplicateGroupsQuery,
    policyDuplicateGroupsErrorMessage,
  } = queries;
  const { policyDuplicateGroups } = data;
  const {
    policyDuplicateReviewNotes,
    reviewSubmittingKey,
  } = state;
  const {
    setPolicyDuplicateReviewNotes,
    onReviewPolicyDuplicateGroup,
  } = actions;

  return (
    <Box id="admin-policy-duplicate-groups" sx={{ scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                데이터 품질 리뷰
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                정책 중복 review queue
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                `YOUTH`와 `BOKJIRO_LOCAL`에서 title/host 기준으로 반복되는 정책 묶음을 최근 review queue로 보여줍니다. broad parser 변경 전, 실제 운영자가 중복 검토 우선순위를 잡는 용도입니다.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {ADMIN_QUEUE_STATUS_OPTIONS.map((option) => (
                <Chip
                  key={option.value}
                  label={option.label}
                  clickable
                  color={policyDuplicateStatusFilter === option.value ? "primary" : "default"}
                  variant={policyDuplicateStatusFilter === option.value ? "filled" : "outlined"}
                  onClick={() => onPolicyDuplicateStatusFilterChange(option.value)}
                />
              ))}
            </Stack>

            {policyDuplicateGroupsQuery.isLoading && (
              <SectionLoadingCard
                title="정책 중복 review 로딩 중"
                description="최근 duplicate title/host 묶음을 불러오는 중입니다."
              />
            )}

            {policyDuplicateGroupsQuery.isError && (
              <SectionErrorCard
                title="정책 중복 review 로드 실패"
                description="운영 duplicate review queue를 읽지 못했습니다."
                message={policyDuplicateGroupsErrorMessage}
                onRetry={() => policyDuplicateGroupsQuery.refetch()}
              />
            )}

            {policyDuplicateGroups && !policyDuplicateGroupsQuery.isLoading && !policyDuplicateGroupsQuery.isError && (
              <Stack spacing={2}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)" } }}>
                  <MetricCard
                    title="열린 중복 묶음"
                    value={formatNumber(policyDuplicateGroups.openGroupCount)}
                    description="아직 운영 검토가 필요한 duplicate title/host 묶음"
                  />
                  <MetricCard
                    title="최근 24시간 신규"
                    value={formatNumber(policyDuplicateGroups.recentOpenGroupCount24h)}
                    description="지난 24시간 안에 새로 생긴 duplicate 묶음"
                  />
                  <MetricCard
                    title="열린 관련 row"
                    value={formatNumber(policyDuplicateGroups.openDuplicateRowCount)}
                    description="현재 열린 duplicate 묶음에 포함된 row 수"
                  />
                  <MetricCard
                    title="표시 묶음"
                    value={formatNumber(policyDuplicateGroups.recentGroups?.length ?? 0)}
                    description="최근 duplicate review 샘플"
                  />
                </Box>

                {(policyDuplicateGroups.recentGroups?.length ?? 0) === 0 ? (
                  <Alert severity="success">
                    {policyDuplicateStatusFilter === "OPEN"
                      ? "현재 열린 정책 중복 review 묶음이 없습니다."
                      : policyDuplicateStatusFilter === "REVIEWED"
                        ? "표시할 처리완료 중복 review 묶음이 없습니다."
                        : "표시할 정책 중복 review 묶음이 없습니다."}
                  </Alert>
                ) : (
                  <CompactListCard
                    title="최근 정책 중복 묶음"
                    description="duplicate count가 큰 묶음부터 표시합니다."
                    items={policyDuplicateGroups.recentGroups ?? []}
                    renderItem={(item) => {
                      const requestKey = `duplicate-${item.sourceType}-${item.title}-${item.hostOrgKey ?? ""}`;
                      return (
                        <Box
                          key={requestKey}
                          sx={{ p: 1.75, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                        >
                          <Stack spacing={1}>
                            <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                              <Box sx={{ minWidth: 0 }}>
                                <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                  {item.title}
                                </Typography>
                                <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                  {formatSourceType(item.sourceType)} · {item.hostOrgLabel || "기관명 없음"} · 최신 row {formatDateTime(item.latestCreatedAt)}
                                </Typography>
                              </Box>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap justifyContent={{ xs: "flex-start", md: "flex-end" }}>
                                {item.reviewClass && (
                                  <Chip
                                    label={formatPolicyDuplicateReviewClass(item.reviewClass)}
                                    size="small"
                                    sx={{
                                      bgcolor: INFO_BG,
                                      color: INFO_TEXT,
                                      border: `1px solid ${INFO_BORDER}`,
                                      fontWeight: 700,
                                      alignSelf: { xs: "flex-start", md: "center" },
                                    }}
                                  />
                                )}
                                <Chip
                                  label={`${formatNumber(item.duplicateCount)}건 중복`}
                                  size="small"
                                  sx={{
                                    bgcolor: WARNING_BG,
                                    color: WARNING_TEXT,
                                    border: `1px solid ${WARNING_BORDER}`,
                                    fontWeight: 700,
                                    alignSelf: { xs: "flex-start", md: "center" },
                                  }}
                                />
                              </Stack>
                            </Stack>
                            <Typography sx={{ fontSize: 13, color: INK2, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                              sourceIds {item.sourceIds}
                            </Typography>
                            {item.status === "REVIEWED" && (
                              <Alert severity="success" sx={{ py: 0 }}>
                                {`처리완료 · ${formatMaskedUserKey(item.reviewedByUserKey, "운영자")} · ${formatDateTime(item.reviewedAt)}`}
                                {formatAdminReviewNoteSuffix(item.reviewNote)}
                              </Alert>
                            )}
                            <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
                              <TextField
                                size="small"
                                placeholder="운영 메모 (선택)"
                                value={policyDuplicateReviewNotes[requestKey] ?? ""}
                                onChange={(event) => setPolicyDuplicateReviewNotes((prev) => ({
                                  ...prev,
                                  [requestKey]: event.target.value,
                                }))}
                                sx={{ flex: 1 }}
                                disabled={item.status === "REVIEWED"}
                              />
                              <Button
                                size="small"
                                variant="outlined"
                                disabled={item.status === "REVIEWED" || reviewSubmittingKey === requestKey}
                                onClick={() => onReviewPolicyDuplicateGroup(item)}
                                sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                              >
                                {item.status === "REVIEWED"
                                  ? "처리완료됨"
                                  : reviewSubmittingKey === requestKey ? "처리 중..." : "처리완료"}
                              </Button>
                            </Stack>
                          </Stack>
                        </Box>
                      );
                    }}
                  />
                )}
              </Stack>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
