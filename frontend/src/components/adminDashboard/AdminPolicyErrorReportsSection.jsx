import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
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
  INK,
  INK3,
  PANEL_BG,
  PANEL_LINE,
} from "./AdminDashboardUiTokens";
import AdminPolicyCorrectionHistory from "./AdminPolicyCorrectionHistory";
import AdminPolicyErrorReportItem from "./AdminPolicyErrorReportItem";
import { formatNumber } from "../../lib/adminDashboardDisplay";

export default function AdminPolicyErrorReportsSection({
  filters,
  queries,
  data,
  state,
  actions,
}) {
  const {
    policyErrorStatusFilter,
    onPolicyErrorStatusFilterChange,
  } = filters;
  const {
    regionOptionsQuery,
    regionOptionsErrorMessage,
    policyRegionCorrectionsQuery,
    policyRegionCorrectionsErrorMessage,
    policyFieldCorrectionsQuery,
    policyFieldCorrectionsErrorMessage,
    policyErrorReportsQuery,
    policyErrorReportsErrorMessage,
  } = queries;
  const {
    policyErrorReports,
    regionOptions,
    policyRegionCorrections,
    policyFieldCorrections,
  } = data;
  const {
    reviewSubmittingKey,
    latestRegionAuditResult,
    policyRegionCorrectionInputs,
    policyFieldCorrectionInputs,
    policyErrorReviewNotes,
  } = state;
  const {
    onRunPolicyRegionAudit,
    updatePolicyRegionCorrectionInput,
    updatePolicyFieldCorrectionInput,
    setPolicyErrorReviewNotes,
    onApplySuggestedRegionCorrection,
    onApplyPolicyRegionCorrection,
    onApplyPolicyFieldCorrection,
    onReviewPolicyErrorReport,
    onRevertPolicyRegionCorrection,
  } = actions;

  return (
    <Box id="admin-policy-error-reports" sx={{ scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                사용자 제보
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                정책 오류 제보 대기열
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                정책 상세에서 사용자가 보낸 오류 제보를 최근 열린 순서대로 봅니다. 지역, 기간, 자격조건, 링크 같은 데이터 품질 문제를 운영에서 빠르게 분류하는 용도입니다.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {ADMIN_QUEUE_STATUS_OPTIONS.map((option) => (
                <Chip
                  key={option.value}
                  label={option.label}
                  clickable
                  color={policyErrorStatusFilter === option.value ? "primary" : "default"}
                  variant={policyErrorStatusFilter === option.value ? "filled" : "outlined"}
                  onClick={() => onPolicyErrorStatusFilterChange(option.value)}
                />
              ))}
              <Button
                size="small"
                variant="outlined"
                disabled={reviewSubmittingKey === "policy-region-audit"}
                onClick={onRunPolicyRegionAudit}
                sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
              >
                {reviewSubmittingKey === "policy-region-audit" ? "감사 중..." : "지역 감사 실행"}
              </Button>
            </Stack>

            {latestRegionAuditResult && (
              <Alert severity={latestRegionAuditResult.createdReportCount > 0 ? "warning" : "info"}>
                {`지역 감사 완료 · 스캔 ${formatNumber(latestRegionAuditResult.scannedCount)}건 · 후보 ${formatNumber(latestRegionAuditResult.candidateCount)}건 · 신규 제보 ${formatNumber(latestRegionAuditResult.createdReportCount)}건`}
              </Alert>
            )}

            {regionOptionsQuery.isError && (
              <Alert severity="warning">{regionOptionsErrorMessage}</Alert>
            )}

            {policyRegionCorrectionsQuery.isError && (
              <Alert severity="warning">{policyRegionCorrectionsErrorMessage}</Alert>
            )}

            {policyFieldCorrectionsQuery.isError && (
              <Alert severity="warning">{policyFieldCorrectionsErrorMessage}</Alert>
            )}

            {policyErrorReportsQuery.isLoading && (
              <SectionLoadingCard
                title="정책 오류 제보 로딩 중"
                description="최근 열린 오류 제보를 불러오는 중입니다."
              />
            )}

            {policyErrorReportsQuery.isError && (
              <SectionErrorCard
                title="정책 오류 제보 로드 실패"
                description="정책 상세에서 접수된 제보 대기열을 읽지 못했습니다."
                message={policyErrorReportsErrorMessage}
                onRetry={() => policyErrorReportsQuery.refetch()}
              />
            )}

            {policyErrorReports && !policyErrorReportsQuery.isLoading && !policyErrorReportsQuery.isError && (
              <Stack spacing={2}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)" } }}>
                  <MetricCard
                    title="열린 제보"
                    value={formatNumber(policyErrorReports.openCount)}
                    description="아직 운영 확인이 필요한 오류 제보"
                  />
                  <MetricCard
                    title="최근 24시간 신규"
                    value={formatNumber(policyErrorReports.recentOpenCount24h)}
                    description="지난 24시간 동안 새로 열린 오류 제보"
                  />
                  <MetricCard
                    title="표시 제보"
                    value={formatNumber(policyErrorReports.recentReports?.length ?? 0)}
                    description="최근 열린 제보 샘플"
                  />
                </Box>

                {(policyErrorReports.recentReports?.length ?? 0) === 0 ? (
                  <Alert severity="success">
                    {policyErrorStatusFilter === "OPEN"
                      ? "현재 열린 정책 오류 제보가 없습니다."
                      : policyErrorStatusFilter === "REVIEWED"
                        ? "표시할 처리완료 정책 오류 제보가 없습니다."
                        : "표시할 정책 오류 제보가 없습니다."}
                  </Alert>
                ) : (
                  <CompactListCard
                    title="최근 정책 오류 제보"
                    description="가장 최근 열린 제보부터 표시합니다."
                    items={policyErrorReports.recentReports ?? []}
                    renderItem={(item) => (
                      <AdminPolicyErrorReportItem
                        key={item.reportId}
                        item={item}
                        regionOptionsQuery={regionOptionsQuery}
                        regionOptions={regionOptions}
                        reviewSubmittingKey={reviewSubmittingKey}
                        policyRegionCorrectionInputs={policyRegionCorrectionInputs}
                        updatePolicyRegionCorrectionInput={updatePolicyRegionCorrectionInput}
                        policyFieldCorrectionInputs={policyFieldCorrectionInputs}
                        updatePolicyFieldCorrectionInput={updatePolicyFieldCorrectionInput}
                        policyErrorReviewNotes={policyErrorReviewNotes}
                        setPolicyErrorReviewNotes={setPolicyErrorReviewNotes}
                        onApplySuggestedRegionCorrection={onApplySuggestedRegionCorrection}
                        onApplyPolicyRegionCorrection={onApplyPolicyRegionCorrection}
                        onApplyPolicyFieldCorrection={onApplyPolicyFieldCorrection}
                        onReviewPolicyErrorReport={onReviewPolicyErrorReport}
                      />
                    )}
                  />
                )}

                <AdminPolicyCorrectionHistory
                  policyRegionCorrections={policyRegionCorrections}
                  policyFieldCorrections={policyFieldCorrections}
                  reviewSubmittingKey={reviewSubmittingKey}
                  onRevertPolicyRegionCorrection={onRevertPolicyRegionCorrection}
                />
              </Stack>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
