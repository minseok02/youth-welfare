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
} from "./AdminDashboardUiTokens";
import {
  formatAdminDisplayText,
  formatAdminReviewNoteSuffix,
  formatAdminRoutePath,
  formatDateTime,
  formatMaskedEmail,
  formatMaskedUserKey,
  formatNumber,
} from "../../lib/adminDashboardDisplay";

export default function AdminSupportInquiriesSection({
  filters,
  queries,
  data,
  state,
  actions,
}) {
  const {
    supportInquiryStatusFilter,
    onSupportInquiryStatusFilterChange,
  } = filters;
  const {
    supportInquiriesQuery,
    supportInquiriesErrorMessage,
  } = queries;
  const { supportInquiries } = data;
  const {
    supportInquiryReviewNotes,
    reviewSubmittingKey,
  } = state;
  const {
    setSupportInquiryReviewNotes,
    onReviewSupportInquiry,
  } = actions;

  return (
    <Box id="admin-support-inquiries" sx={{ scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                사용자 문의
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                서비스 문의 recent queue
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                로그인, 추천, 챗봇, 검색, 알림처럼 서비스를 쓰다가 막힌 내용을 최근 열린 순서대로 봅니다. 정책 데이터 오류 제보와는 별도 queue입니다.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {ADMIN_QUEUE_STATUS_OPTIONS.map((option) => (
                <Chip
                  key={option.value}
                  label={option.label}
                  clickable
                  color={supportInquiryStatusFilter === option.value ? "primary" : "default"}
                  variant={supportInquiryStatusFilter === option.value ? "filled" : "outlined"}
                  onClick={() => onSupportInquiryStatusFilterChange(option.value)}
                />
              ))}
            </Stack>

            {supportInquiriesQuery.isLoading && (
              <SectionLoadingCard
                title="서비스 문의 로딩 중"
                description="최근 열린 서비스 문의를 불러오는 중입니다."
              />
            )}

            {supportInquiriesQuery.isError && (
              <SectionErrorCard
                title="서비스 문의 로드 실패"
                description="서비스 사용 문의 queue를 읽지 못했습니다."
                message={supportInquiriesErrorMessage}
                onRetry={() => supportInquiriesQuery.refetch()}
              />
            )}

            {supportInquiries && !supportInquiriesQuery.isLoading && !supportInquiriesQuery.isError && (
              <Stack spacing={2}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)" } }}>
                  <MetricCard
                    title="열린 문의"
                    value={formatNumber(supportInquiries.openCount)}
                    description="아직 운영 확인이 필요한 서비스 문의"
                  />
                  <MetricCard
                    title="최근 24시간 신규"
                    value={formatNumber(supportInquiries.recentOpenCount24h)}
                    description="지난 24시간 동안 새로 열린 서비스 문의"
                  />
                  <MetricCard
                    title="표시 문의"
                    value={formatNumber(supportInquiries.recentInquiries?.length ?? 0)}
                    description="최근 열린 문의 샘플"
                  />
                </Box>

                {(supportInquiries.recentInquiries?.length ?? 0) === 0 ? (
                  <Alert severity="success">
                    {supportInquiryStatusFilter === "OPEN"
                      ? "현재 열린 서비스 문의가 없습니다."
                      : supportInquiryStatusFilter === "REVIEWED"
                        ? "표시할 처리완료 서비스 문의가 없습니다."
                        : "표시할 서비스 문의가 없습니다."}
                  </Alert>
                ) : (
                  <CompactListCard
                    title="최근 서비스 문의"
                    description="가장 최근 열린 문의부터 표시합니다."
                    items={supportInquiries.recentInquiries ?? []}
                    renderItem={(item) => (
                      <Box
                        key={item.inquiryId}
                        sx={{ p: 1.75, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                      >
                        <Stack spacing={1}>
                          <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                {item.categoryLabel}
                              </Typography>
                              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                {formatMaskedEmail(item.contactEmail)} · {formatAdminRoutePath(item.routePath)} · {formatDateTime(item.createdAt)}
                              </Typography>
                            </Box>
                            <Chip
                              label={item.categoryLabel}
                              size="small"
                              sx={{
                                bgcolor: INFO_BG,
                                color: INFO_TEXT,
                                border: `1px solid ${INFO_BORDER}`,
                                fontWeight: 700,
                                alignSelf: { xs: "flex-start", md: "center" },
                              }}
                            />
                          </Stack>
                          <Typography sx={{ fontSize: 13, color: INK2 }}>
                            {formatAdminDisplayText(item.message)}
                          </Typography>
                          <Typography sx={{ fontSize: 12, color: INK3 }}>
                            문의자 {formatMaskedUserKey(item.userKey, "비로그인/미연결")}
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
                              value={supportInquiryReviewNotes[item.inquiryId] ?? ""}
                              onChange={(event) => setSupportInquiryReviewNotes((prev) => ({
                                ...prev,
                                [item.inquiryId]: event.target.value,
                              }))}
                              sx={{ flex: 1 }}
                              disabled={item.status === "REVIEWED"}
                            />
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={item.status === "REVIEWED" || reviewSubmittingKey === `support-${item.inquiryId}`}
                              onClick={() => onReviewSupportInquiry(item.inquiryId)}
                              sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                            >
                              {item.status === "REVIEWED"
                                ? "처리완료됨"
                                : reviewSubmittingKey === `support-${item.inquiryId}` ? "처리 중..." : "처리완료"}
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
