import {
  Box,
  Stack,
  Typography,
} from "@mui/material";
import { CompactListCard } from "./AdminDashboardUi";
import {
  ACCENT,
  INK,
  INK2,
  INK3,
  PANEL_LINE,
} from "./AdminDashboardUiTokens";
import {
  formatActorType,
  formatBooleanLabel,
  formatDateTime,
  formatNumber,
  formatSearchStatusFilter,
  formatSortKey,
  formatSourceType,
} from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_FOCUS_KEYS,
  ADMIN_DASHBOARD_LIST_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export default function AdminSearchTriageLists({ searchFailures }) {
  return (
    <>
      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
        <CompactListCard
          title="0건 검색 지역"
          description="지역 단위 0건 검색 상위"
          items={searchFailures.zeroResultRegions}
          renderItem={(item) => (
            <Box key={`${item.sido}-${item.sgg}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
              <Stack direction="row" justifyContent="space-between" spacing={2}>
                <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.sido || "전국"} {item.sgg || ""}</Typography>
                <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT }}>{formatNumber(item.searchCount)}</Typography>
              </Stack>
            </Box>
          )}
        />
        <CompactListCard
          title="0건 검색 필터 패턴"
          description="필터 조합별 0건 검색"
          items={searchFailures.zeroResultFilterPatterns}
          renderItem={(item) => (
            <Box key={`${item.statusFilter}-${item.category}-${item.sourceType}-${item.onlineApply}-${item.includeClosed}-${item.sortKey}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
              <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>
                {item.category || "전체"} · {item.sourceType ? formatSourceType(item.sourceType) : "전체"}
              </Typography>
              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                {formatSearchStatusFilter(item.statusFilter)} / {formatBooleanLabel(item.onlineApply, "온라인만", "온라인 포함")} / {formatBooleanLabel(item.includeClosed, "마감 포함", "마감 제외")} / {formatSortKey(item.sortKey)}
              </Typography>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, mt: 0.75 }}>
                {formatNumber(item.searchCount)}
              </Typography>
            </Box>
          )}
        />
      </Box>

      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr 1fr" } }}>
        <CompactListCard
          title="재시도 묶음"
          description="같은 사용자 주체가 반복한 실패 검색"
          items={searchFailures.retryGroups}
          renderItem={(item) => (
            <Box key={`${item.actorType}-${item.actorKey}-${item.keyword}-${item.latestSearchedAt}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
              <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.keyword || "키워드 없음"}</Typography>
              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                {formatActorType(item.actorType)} · {item.actorKey || "익명"}
              </Typography>
              <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                재시도 {formatNumber(item.retryCount)} · {formatDateTime(item.firstSearchedAt)} ~ {formatDateTime(item.latestSearchedAt)}
              </Typography>
            </Box>
          )}
        />
        <CompactListCard
          title="복구된 묶음"
          description="나중에 결과가 생긴 검색 묶음"
          cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.searchRecoveredGroups)}
          items={searchFailures.recoveredSearchGroups}
          renderItem={(item, index) => (
            <Box
              key={`${item.actorType}-${item.actorKey}-${item.keyword}-${item.latestRecoveredAt}`}
              {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.searchRecovered) : {})}
              {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, "success") : {})}
              sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
            >
              <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.keyword || "키워드 없음"}</Typography>
              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                0건 {formatNumber(item.zeroResultCount)} / 복구 {formatNumber(item.recoveredResultCount)}
              </Typography>
              <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                최근 복구 {formatDateTime(item.latestRecoveredAt)}
              </Typography>
            </Box>
          )}
        />
        <CompactListCard
          title="최근 0건 검색 샘플"
          description="실패 검색 샘플"
          cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.searchRecentZeroResultSamples)}
          items={searchFailures.recentSamples}
          renderItem={(item, index) => (
            <Box
              key={`${item.keyword}-${item.searchedAt}-${item.sido}-${item.sgg}`}
              {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.searchWarning) : {})}
              {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, "warning") : {})}
              sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
            >
              <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.keyword || "키워드 없음"}</Typography>
              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                {item.sido || "전국"} {item.sgg || ""} · {formatDateTime(item.searchedAt)}
              </Typography>
              <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                {item.category || "전체"} / {item.sourceType ? formatSourceType(item.sourceType) : "전체"} / {formatSearchStatusFilter(item.statusFilter)}
              </Typography>
            </Box>
          )}
        />
      </Box>
    </>
  );
}
