import {
  Box,
  Card,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import {
  CompactListCard,
  MetricCard,
  SectionErrorCard,
  SectionLoadingCard,
  TriageSectionTitle,
} from "./AdminDashboardUi";
import {
  INFO_BG,
  INFO_BORDER,
  INFO_TEXT,
  INK,
  INK3,
  PANEL_BG,
  PANEL_LINE,
  WARNING_TEXT,
} from "./AdminDashboardUiTokens";
import {
  formatCompactJson,
  formatDateTime,
  formatNumber,
  formatStatusLabel,
} from "../../lib/adminDashboardDisplay";

export default function AdminRecommendationRunSummarySection({
  recommendationRunSummaryQuery,
  recommendationRunSummaryErrorMessage,
  recommendationRunSummary,
}) {
  return (
    <>
      {recommendationRunSummaryQuery.isLoading && (
        <SectionLoadingCard
          title="추천 실행 로그 로딩 중"
          description="추천 생성 run outcome, 저장량, 지연시간, 최근 실행 샘플을 불러오는 중입니다."
        />
      )}

      {recommendationRunSummaryQuery.isError && (
        <SectionErrorCard
          title="추천 실행 로그 로드 실패"
          description="신규 recommendation_run_logs migration 적용 상태와 admin read 권한을 확인해야 합니다."
          message={recommendationRunSummaryErrorMessage}
          onRetry={() => recommendationRunSummaryQuery.refetch()}
        />
      )}

      {recommendationRunSummary && (
        <Box id="admin-recommendation-run-summary" sx={{ scrollMarginTop: 96 }}>
          <TriageSectionTitle
            eyebrow="추천 실행 로그"
            title="추천 생성 run 상태"
            description="recommendation_run_logs 기준으로 생성 결과, 빈 후보, 오류, 평균 지연시간을 확인합니다."
          />
          <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
            <MetricCard
              title={`${recommendationRunSummary.windowDays}일 실행`}
              value={formatNumber(recommendationRunSummary.totalRuns)}
              description={`최근 실행 ${formatDateTime(recommendationRunSummary.latestRunAt)}`}
            />
            <MetricCard
              title="성공 / 오류"
              value={`${formatNumber(recommendationRunSummary.successRuns)} / ${formatNumber(recommendationRunSummary.errorRuns)}`}
              description={`후보 없음 ${formatNumber(recommendationRunSummary.noCandidateRuns)}`}
              descriptionColor={Number(recommendationRunSummary.errorRuns) > 0 ? WARNING_TEXT : INK3}
            />
            <MetricCard
              title="저장 추천"
              value={formatNumber(recommendationRunSummary.savedCount)}
              description={`평균 저장 ${formatNumber(recommendationRunSummary.averageSavedCount)}`}
            />
            <MetricCard
              title="평균 지연"
              value={`${formatNumber(recommendationRunSummary.averageDurationMs)}ms`}
              description={`개인화 실행 ${formatNumber(recommendationRunSummary.personalRuns)}`}
            />
          </Box>

          <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", xl: "0.8fr 1.2fr" } }}>
            <CompactListCard
              title="outcome 분포"
              description="선택한 기간 내 추천 실행 outcome별 집계"
              items={recommendationRunSummary.outcomeBreakdowns}
              renderItem={(item) => (
                <Box key={item.outcome} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                  <Stack direction="row" justifyContent="space-between" spacing={2}>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography sx={{ fontSize: 13, fontWeight: 800, color: INK }}>{formatStatusLabel(item.outcome)}</Typography>
                      <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                        저장 {formatNumber(item.savedCount)} · 평균 {formatNumber(item.averageDurationMs)}ms
                      </Typography>
                    </Box>
                    <Chip
                      label={formatNumber(item.runCount)}
                      size="small"
                      sx={{ bgcolor: INFO_BG, color: INFO_TEXT, border: `1px solid ${INFO_BORDER}`, fontWeight: 700 }}
                    />
                  </Stack>
                </Box>
              )}
            />

            <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>최근 실행 샘플</Typography>
                <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                  최신 run 기준 후보 수, 저장 수, AI status count를 확인합니다.
                </Typography>
                <TableContainer sx={{ mt: 2, border: `1px solid ${PANEL_LINE}`, borderRadius: 2, overflow: "auto" }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>시간</TableCell>
                        <TableCell>outcome</TableCell>
                        <TableCell align="right">후보</TableCell>
                        <TableCell align="right">저장</TableCell>
                        <TableCell align="right">지연</TableCell>
                        <TableCell>AI status</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(recommendationRunSummary.recentRuns ?? []).map((item) => (
                        <TableRow key={item.id}>
                          <TableCell sx={{ whiteSpace: "nowrap" }}>{formatDateTime(item.createdAt)}</TableCell>
                          <TableCell>{formatStatusLabel(item.outcome)}</TableCell>
                          <TableCell align="right">{formatNumber(item.retrievedCount)}</TableCell>
                          <TableCell align="right">{formatNumber(item.savedCount)}</TableCell>
                          <TableCell align="right">{formatNumber(item.durationMs)}ms</TableCell>
                          <TableCell sx={{ maxWidth: 260, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                            {formatCompactJson(item.aiStatusCountsJson)}
                          </TableCell>
                        </TableRow>
                      ))}
                      {(recommendationRunSummary.recentRuns ?? []).length === 0 && (
                        <TableRow>
                          <TableCell colSpan={6} sx={{ color: INK3 }}>
                            최근 실행 로그가 없습니다.
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CardContent>
            </Card>
          </Box>
        </Box>
      )}
    </>
  );
}
