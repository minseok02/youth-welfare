import {
  Box,
  Button,
  Stack,
  Typography,
} from "@mui/material";
import { CompactListCard } from "./AdminDashboardUi";
import {
  INK,
  INK2,
  INK3,
  PANEL_LINE,
} from "./AdminDashboardUiTokens";
import { formatSourceType } from "../../lib/adminDashboardDisplay";

export default function AdminPolicyCorrectionHistory({
  policyRegionCorrections,
  policyFieldCorrections,
  reviewSubmittingKey,
  onRevertPolicyRegionCorrection,
}) {
  return (
    <>
      {policyRegionCorrections?.corrections?.length > 0 && (
        <CompactListCard
          title="지역 보정 이력"
          description="활성 보정은 수집/백필에서 보호되고, 비활성 이력은 감사 추적용으로 남습니다."
          items={policyRegionCorrections.corrections}
          renderItem={(item) => (
            <Box
              key={item.correctionId}
              sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#f8fafc" }}
            >
              <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontSize: 13, fontWeight: 800, color: INK, overflowWrap: "anywhere" }}>
                    {item.policyTitle}
                  </Typography>
                  <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                    {formatSourceType(item.sourceType)} · {item.sourceId || "sourceId 없음"} · {item.correctionScope} · {item.active ? "활성" : "비활성"}
                  </Typography>
                  <Typography sx={{ fontSize: 12, color: INK2, mt: 0.5, overflowWrap: "anywhere" }}>
                    {(item.regions ?? []).length > 0
                      ? item.regions.map((region) => `${region.sidoName} ${region.sggName} (${region.regionCode})`).join(", ")
                      : "전국 처리"}
                  </Typography>
                </Box>
                <Button
                  size="small"
                  variant="outlined"
                  color="warning"
                  disabled={!item.active || reviewSubmittingKey === `policy-region-revert-${item.correctionId}`}
                  onClick={() => onRevertPolicyRegionCorrection(item.correctionId)}
                  sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap", alignSelf: { xs: "stretch", md: "center" } }}
                >
                  {!item.active ? "되돌림 완료" : reviewSubmittingKey === `policy-region-revert-${item.correctionId}` ? "되돌리는 중..." : "되돌리기"}
                </Button>
              </Stack>
            </Box>
          )}
        />
      )}
      {policyFieldCorrections?.corrections?.length > 0 && (
        <CompactListCard
          title="필드 보정 이력"
          description="기간, 링크, 자격조건, 중복 정책 보정 기록입니다."
          items={policyFieldCorrections.corrections}
          renderItem={(item) => (
            <Box
              key={item.correctionId}
              sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#f8fafc" }}
            >
              <Typography sx={{ fontSize: 13, fontWeight: 800, color: INK, overflowWrap: "anywhere" }}>
                {item.policyTitle}
              </Typography>
              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                {formatSourceType(item.sourceType)} · {item.sourceId || "sourceId 없음"} · {item.correctionType}
              </Typography>
              <Typography sx={{ fontSize: 12, color: INK2, mt: 0.5, overflowWrap: "anywhere" }}>
                {item.correctionJson}
              </Typography>
            </Box>
          )}
        />
      )}
    </>
  );
}
