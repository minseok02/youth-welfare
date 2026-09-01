import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Stack,
  Typography,
} from "@mui/material";
import {
  MetricCard,
  SectionErrorCard,
  SectionLoadingCard,
} from "./AdminDashboardUi";
import {
  ACCENT,
  INK,
  INK3,
  PANEL_BG,
  PANEL_LINE,
} from "./AdminDashboardUiTokens";
import {
  formatNumber,
  formatRelativeDateTime,
} from "../../lib/adminDashboardDisplay";

export default function AdminStandardCodeCoverageSection({
  standardCodeCoverageQuery,
  standardCodeCoverageErrorMessage,
  standardCodeCoverage,
}) {
  return (
    <Box id="admin-standard-code-coverage" sx={{ scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                프로필 정합성
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                선택 프로필 입력 현황
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                추천 정확도에 영향을 주는 선택 프로필 4개가 얼마나 채워졌는지, 기본 회원정보와 상세 프로필 값이 어긋난 곳은 없는지 낮은 빈도로 봅니다.
              </Typography>
            </Box>

            {standardCodeCoverageQuery.isLoading && (
              <SectionLoadingCard
                title="선택 프로필 입력 현황 로딩 중"
                description="사용자 선택 프로필 입력 현황을 계산하는 중입니다."
              />
            )}

            {standardCodeCoverageQuery.isError && (
              <SectionErrorCard
                title="선택 프로필 입력 현황 로드 실패"
                description="선택 프로필 입력 현황 집계를 불러오지 못했습니다."
                message={standardCodeCoverageErrorMessage}
                onRetry={() => standardCodeCoverageQuery.refetch()}
              />
            )}

            {standardCodeCoverage && !standardCodeCoverageQuery.isLoading && !standardCodeCoverageQuery.isError && (
              <Stack spacing={2}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", xl: "repeat(4, 1fr)" } }}>
                  <MetricCard
                    title="전체 사용자"
                    value={formatNumber(standardCodeCoverage.totalUsers)}
                    description={formatRelativeDateTime(standardCodeCoverage.generatedAt)}
                  />
                  <MetricCard
                    title="1개 이상 입력"
                    value={formatNumber(standardCodeCoverage.usersWithAnyStandardCode)}
                    description="선택 프로필 코드가 하나라도 채워진 사용자"
                  />
                  <MetricCard
                    title="전부 미입력"
                    value={formatNumber(standardCodeCoverage.usersMissingAllStandardCodes)}
                    description="선택 프로필 코드 4개가 모두 비어 있는 사용자"
                    focusTarget
                  />
                  <MetricCard
                    title="안전 보정 후보"
                    value={formatNumber(standardCodeCoverage.safeReconcileCandidateRows)}
                    description="충돌 없이 기본/상세 정보 사이에 복사 가능한 값"
                  />
                </Box>

                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)", xl: "repeat(4, 1fr)" } }}>
                  <MetricCard title="주거형태 입력" value={formatNumber(standardCodeCoverage.houseTenureFilled)} description="없음 선택까지 포함한 응답 수" />
                  <MetricCard title="주택유형 입력" value={formatNumber(standardCodeCoverage.housingTypeFilled)} description="없음 선택까지 포함한 응답 수" />
                  <MetricCard title="기초생활수급권자 입력" value={formatNumber(standardCodeCoverage.basicLivingRecipientTypeFilled)} description="없음 선택까지 포함한 응답 수" />
                  <MetricCard title="장애등급 입력" value={formatNumber(standardCodeCoverage.disabilityGradeFilled)} description="없음 선택까지 포함한 응답 수" />
                </Box>

                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={`상세 프로필 있음 ${formatNumber(standardCodeCoverage.usersWithProfileRow)}`} size="small" variant="outlined" />
                  <Chip label={`상세 프로필 없음 ${formatNumber(standardCodeCoverage.usersWithoutProfileRow)}`} size="small" variant="outlined" />
                  <Chip label={`기본/상세 값 충돌 ${formatNumber(standardCodeCoverage.conflictingValueGapRows)}`} size="small" variant="outlined" />
                  <Chip label={`상세에만 있는 값 ${formatNumber(standardCodeCoverage.profileOnlyGapRows)}`} size="small" variant="outlined" />
                  <Chip label={`기본 정보에만 있는 값 ${formatNumber(standardCodeCoverage.userOnlyGapRows)}`} size="small" variant="outlined" />
                </Stack>
              </Stack>
            )}

            {!standardCodeCoverage && !standardCodeCoverageQuery.isLoading && !standardCodeCoverageQuery.isError && (
              <Alert severity="info">선택 프로필 입력 현황 데이터가 없습니다.</Alert>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
