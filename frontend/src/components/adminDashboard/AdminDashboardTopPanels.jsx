import {
  Box,
  Button,
  MenuItem,
  Select,
  Stack,
  Typography,
} from "@mui/material";
import {
  ACCENT,
  INK,
  INK2,
  INK3,
  PANEL_BG,
} from "./AdminDashboardUiTokens";

export function AdminDashboardTopHeader({
  windowDays,
  onWindowDaysChange,
  isRefreshing,
  onRefreshAll,
}) {
  return (
    <Stack direction={{ xs: "column", lg: "row" }} justifyContent="space-between" alignItems={{ xs: "flex-start", lg: "center" }} spacing={2}>
      <Box>
        <Typography sx={{ fontSize: 32, fontWeight: 900, color: INK, letterSpacing: "-0.03em" }}>
          운영 추천 대시보드
        </Typography>
        <Typography sx={{ fontSize: 14, color: INK3, mt: 1 }}>
          추천 검토 상태, 1순위 선두 신호, 사용자 구성을 한 화면에서 확인합니다.
        </Typography>
      </Box>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ xs: "stretch", sm: "center" }}>
        <Stack spacing={0.5} sx={{ minWidth: { xs: "100%", sm: 180 } }}>
          <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>요약 기간</Typography>
          <Select
            size="small"
            value={windowDays}
            onChange={(event) => onWindowDaysChange(Number(event.target.value))}
            sx={{ minWidth: 120, bgcolor: PANEL_BG }}
          >
            <MenuItem value={7}>최근 7일</MenuItem>
            <MenuItem value={14}>최근 14일</MenuItem>
            <MenuItem value={30}>최근 30일</MenuItem>
          </Select>
        </Stack>
        <Button
          variant="contained"
          onClick={onRefreshAll}
          disabled={isRefreshing}
          sx={{
            alignSelf: { xs: "stretch", sm: "flex-end" },
            borderRadius: 999,
            px: 2.25,
            py: 1.1,
            textTransform: "none",
            fontWeight: 800,
            bgcolor: ACCENT,
            boxShadow: "none",
            "&:hover": { bgcolor: "#1d4ed8", boxShadow: "none" },
          }}
        >
          {isRefreshing ? "갱신 중..." : "전체 새로고침"}
        </Button>
      </Stack>
    </Stack>
  );
}
