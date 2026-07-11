import {
  Box,
  Chip,
  Stack,
  Typography,
} from "@mui/material";
import {
  CompactListCard,
  SectionErrorCard,
  SectionLoadingCard,
  ServiceListCard,
} from "./AdminDashboardUi";
import {
  INK,
  INK2,
  PANEL_LINE,
} from "./AdminDashboardUiTokens";
import { formatNumber } from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_LIST_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

function FacetDistributionCard({
  title,
  description,
  items,
}) {
  return (
    <CompactListCard
      title={title}
      description={description}
      items={items}
      renderItem={(group) => (
        <Box key={group.facetKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
          <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{group.label}</Typography>
          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap mt={1}>
            {group.buckets?.map((bucket) => (
              <Chip
                key={`${group.facetKey}-${bucket.label}`}
                label={`${bucket.label} · 행 ${formatNumber(bucket.rowCount)} / 서비스 ${formatNumber(bucket.distinctServices)}`}
                size="small"
                sx={{
                  bgcolor: "#f8fafc",
                  color: INK2,
                  border: `1px solid ${PANEL_LINE}`,
                  height: "auto",
                  maxWidth: "100%",
                  "& .MuiChip-label": {
                    display: "block",
                    whiteSpace: "normal",
                    overflowWrap: "anywhere",
                    wordBreak: "break-word",
                    py: 0.5,
                  },
                }}
              />
            ))}
          </Stack>
        </Box>
      )}
    />
  );
}

export default function AdminRecommendationBreakdownsSection({
  breakdownQuery,
  breakdownErrorMessage,
  breakdowns,
}) {
  return (
    <>
      {breakdownQuery.isLoading && (
        <SectionLoadingCard
          title="추천 상세 진단 로딩 중"
          description="반복 노출 서비스, 1순위 분포 선두, 세부 샘플을 불러오는 중입니다."
        />
      )}

      {breakdownQuery.isError && (
        <SectionErrorCard
          title="추천 상세 진단 로드 실패"
          description="요약 데이터가 살아 있으면 상단 추천 검토 상태와 최근 추천 집중도는 계속 볼 수 있습니다."
          message={breakdownErrorMessage}
          onRetry={() => breakdownQuery.refetch()}
        />
      )}

      {breakdowns && (
        <Box id="admin-recommendation-breakdowns" sx={{ scrollMarginTop: 96 }}>
          <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
            <ServiceListCard
              title="반복 노출 상위 서비스"
              items={breakdowns.topRepeatedServices}
              countLabel="rowCount"
              focusTarget
              cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.topRepeatedServices)}
            />
            <ServiceListCard title="1순위 분포 선두 서비스" items={breakdowns.top1Services} countLabel="usersAsTop1" />
          </Box>

          <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
            <FacetDistributionCard
              title="온통청년 공식 속성 분포"
              description="최근 추천 배치 기준 온통청년 공식 속성 분포"
              items={breakdowns.youthOfficialFacetGroups}
            />
            <FacetDistributionCard
              title="정부24 토큰 속성 분포"
              description="최근 추천 배치 기준 정부24 사용자구분/지원유형 토큰 분포"
              items={breakdowns.gov24FacetGroups}
            />
          </Box>
        </Box>
      )}
    </>
  );
}
