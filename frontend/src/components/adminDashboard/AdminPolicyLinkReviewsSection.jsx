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
  formatDate,
  formatDateTime,
  formatMaskedUserKey,
  formatNumber,
  formatPolicyLinkReviewBucket,
  formatSourceType,
} from "../../lib/adminDashboardDisplay";

export default function AdminPolicyLinkReviewsSection({
  filters,
  queries,
  data,
  state,
  actions,
}) {
  const {
    policyLinkStatusFilter,
    onPolicyLinkStatusFilterChange,
  } = filters;
  const {
    policyLinkReviewsQuery,
    policyLinkReviewsErrorMessage,
  } = queries;
  const { policyLinkReviews } = data;
  const {
    policyLinkReviewNotes,
    reviewSubmittingKey,
  } = state;
  const {
    setPolicyLinkReviewNotes,
    onReviewPolicyLink,
  } = actions;

  return (
    <Box id="admin-policy-link-reviews" sx={{ scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                링크 품질 리뷰
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                정책 링크 review queue
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                현재 노출될 수 있는 정책 중 `detail URL`과 대표 참고 링크가 모두 비어 있는 항목만 recent queue로 보여줍니다. source contract와 실제 사용자 체감을 분리해 운영자가 review할 수 있게 둔 경계입니다.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {ADMIN_QUEUE_STATUS_OPTIONS.map((option) => (
                <Chip
                  key={option.value}
                  label={option.label}
                  clickable
                  color={policyLinkStatusFilter === option.value ? "primary" : "default"}
                  variant={policyLinkStatusFilter === option.value ? "filled" : "outlined"}
                  onClick={() => onPolicyLinkStatusFilterChange(option.value)}
                />
              ))}
            </Stack>

            {policyLinkReviewsQuery.isLoading && (
              <SectionLoadingCard
                title="정책 링크 review 로딩 중"
                description="최근 열린 링크 review 후보를 불러오는 중입니다."
              />
            )}

            {policyLinkReviewsQuery.isError && (
              <SectionErrorCard
                title="정책 링크 review 로드 실패"
                description="현재 사용자에게 노출될 수 있는 링크 공백 정책 queue를 읽지 못했습니다."
                message={policyLinkReviewsErrorMessage}
                onRetry={() => policyLinkReviewsQuery.refetch()}
              />
            )}

            {policyLinkReviews && !policyLinkReviewsQuery.isLoading && !policyLinkReviewsQuery.isError && (
              <Stack spacing={2}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)" } }}>
                  <MetricCard
                    title="열린 링크 검토"
                    value={formatNumber(policyLinkReviews.openCount)}
                    description="대표 링크가 비어 있는 현재 노출 후보"
                  />
                  <MetricCard
                    title="최근 24시간 신규"
                    value={formatNumber(policyLinkReviews.recentOpenCount24h)}
                    description="지난 24시간에 새로 열린 링크 review 후보"
                  />
                  <MetricCard
                    title="표시 항목"
                    value={formatNumber(policyLinkReviews.recentReviews?.length ?? 0)}
                    description="최근 링크 review 샘플"
                  />
                </Box>

                {(policyLinkReviews.recentReviews?.length ?? 0) === 0 ? (
                  <Alert severity="success">
                    {policyLinkStatusFilter === "OPEN"
                      ? "현재 열린 정책 링크 review 후보가 없습니다."
                      : policyLinkStatusFilter === "REVIEWED"
                        ? "표시할 처리완료 링크 review가 없습니다."
                        : "표시할 정책 링크 review 항목이 없습니다."}
                  </Alert>
                ) : (
                  <CompactListCard
                    title="최근 정책 링크 review"
                    description="링크 공백이 있는 최신 정책부터 표시합니다."
                    items={policyLinkReviews.recentReviews ?? []}
                    renderItem={(item) => (
                      <Box
                        key={item.serviceId}
                        sx={{ p: 1.75, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                      >
                        <Stack spacing={1}>
                          <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                {item.policyTitle}
                              </Typography>
                              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                {formatSourceType(item.sourceType)} · {item.sourceId || "sourceId 없음"} · {formatDateTime(item.createdAt)}
                              </Typography>
                            </Box>
                            <Chip
                              label="대표 링크 비어있음"
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
                          <Typography sx={{ fontSize: 13, color: INK2 }}>
                            {[
                              item.hostOrgLabel || item.operatingOrgLabel || "기관명 없음",
                              item.categoryMain || item.categorySub
                                ? [item.categoryMain, item.categorySub].filter(Boolean).join(" / ")
                                : "카테고리 없음",
                            ].join(" · ")}
                          </Typography>
                          <Typography sx={{ fontSize: 12, color: INK3 }}>
                            신청 종료 {formatDate(item.applyEndDate)} · 사업 종료 {formatDate(item.endDate)}
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip
                              label={formatPolicyLinkReviewBucket(item.reviewBucket)}
                              size="small"
                              variant="outlined"
                            />
                          </Stack>
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
                              value={policyLinkReviewNotes[item.serviceId] ?? ""}
                              onChange={(event) => setPolicyLinkReviewNotes((prev) => ({
                                ...prev,
                                [item.serviceId]: event.target.value,
                              }))}
                              sx={{ flex: 1 }}
                              disabled={item.status === "REVIEWED"}
                            />
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={item.status === "REVIEWED" || reviewSubmittingKey === `policy-link-${item.serviceId}`}
                              onClick={() => onReviewPolicyLink(item.serviceId)}
                              sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                            >
                              {item.status === "REVIEWED"
                                ? "처리완료됨"
                                : reviewSubmittingKey === `policy-link-${item.serviceId}` ? "처리 중..." : "처리완료"}
                            </Button>
                          </Stack>
                        </Stack>
                      </Box>
                    )}
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
