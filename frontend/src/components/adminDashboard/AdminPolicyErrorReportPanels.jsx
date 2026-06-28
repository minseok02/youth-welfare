import {
  Alert,
  Box,
  Button,
  Chip,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import {
  INFO_BG,
  INFO_BORDER,
  INFO_TEXT,
  INK,
  INK2,
  INK3,
  PANEL_LINE,
  WARNING_BG,
  WARNING_BORDER,
  WARNING_TEXT,
} from "./AdminDashboardUiTokens";
import {
  formatAdminDisplayText,
  formatAdminReviewNoteSuffix,
  formatDateTime,
  formatMaskedUserKey,
  formatSourceType,
  parseRegionCorrectionCodes,
  parseSuggestedRegionCodes,
} from "../../lib/adminDashboardDisplay";

export function PolicyErrorReportHeader({ item }) {
  return (
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
        label={item.reasonLabel}
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
  );
}

export function PolicyErrorReportReporterNote({ item }) {
  return (
    <Typography sx={{ fontSize: 13, color: INK2 }}>
      제보자 {formatMaskedUserKey(item.userKey, "익명")} · {formatAdminDisplayText(item.note, "추가 메모 없음")}
    </Typography>
  );
}

export function PolicyErrorReportReviewStatus({ item }) {
  return (
    <Alert severity="success" sx={{ py: 0 }}>
      {`처리완료 · ${formatMaskedUserKey(item.reviewedByUserKey, "운영자")} · ${formatDateTime(item.reviewedAt)}`}
      {formatAdminReviewNoteSuffix(item.reviewNote)}
    </Alert>
  );
}

export function PolicyRegionCorrectionPanel({
  item,
  regionOptionsQuery,
  regionOptions,
  reviewSubmittingKey,
  regionInput,
  updatePolicyRegionCorrectionInput,
  onApplySuggestedRegionCorrection,
  onApplyPolicyRegionCorrection,
}) {
  const suggestedRegionCodes = parseSuggestedRegionCodes(item.note);
  const regionSubmitting = reviewSubmittingKey?.startsWith(`policy-region-${item.reportId}-`);

  return (
    <Box sx={{ p: 1.5, borderRadius: 1.5, border: `1px solid ${INFO_BORDER}`, bgcolor: INFO_BG }}>
      <Stack spacing={1}>
        <Typography sx={{ fontSize: 12, fontWeight: 800, color: INFO_TEXT }}>
          지역 보정
        </Typography>
        {suggestedRegionCodes.length > 0 && (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Button
              size="small"
              variant="outlined"
              disabled={reviewSubmittingKey === `policy-region-${item.reportId}-suggested`}
              onClick={() => onApplySuggestedRegionCorrection(item, suggestedRegionCodes)}
              sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
            >
              {reviewSubmittingKey === `policy-region-${item.reportId}-suggested` ? "적용 중..." : "감사 후보 적용"}
            </Button>
            {suggestedRegionCodes.map((code) => (
              <Chip
                key={`${item.reportId}-${code}`}
                label={regionOptions.find((region) => region.regionCode === code)?.label ?? code}
                size="small"
                variant="outlined"
              />
            ))}
          </Stack>
        )}
        <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
          <TextField
            size="small"
            placeholder="행정구역코드"
            value={regionInput.regionCodes ?? ""}
            onChange={(event) => updatePolicyRegionCorrectionInput(item.reportId, {
              regionCodes: event.target.value,
              selectedRegionCodes: [],
            })}
            helperText="예: 28110 또는 28110, 28200"
            sx={{ flex: 1.1 }}
            disabled={regionSubmitting}
          />
          <Select
            multiple
            size="small"
            displayEmpty
            value={regionInput.selectedRegionCodes ?? []}
            onChange={(event) => updatePolicyRegionCorrectionInput(item.reportId, {
              selectedRegionCodes: Array.isArray(event.target.value)
                ? event.target.value
                : parseRegionCorrectionCodes(event.target.value),
              regionCodes: "",
            })}
            renderValue={(selected) => {
              if (!selected || selected.length === 0) {
                return "지역 선택";
              }
              return selected
                .map((code) => regionOptions.find((region) => region.regionCode === code)?.label ?? code)
                .join(", ");
            }}
            disabled={regionOptionsQuery.isLoading || regionSubmitting}
            sx={{ flex: 1.4, minWidth: 220 }}
          >
            {regionOptions.map((region) => (
              <MenuItem key={region.regionCode} value={region.regionCode}>
                {region.label}
              </MenuItem>
            ))}
          </Select>
          <TextField
            size="small"
            placeholder="보정 메모"
            value={regionInput.correctionNote ?? ""}
            onChange={(event) => updatePolicyRegionCorrectionInput(item.reportId, {
              correctionNote: event.target.value,
            })}
            sx={{ flex: 1.2 }}
            disabled={regionSubmitting}
          />
          <Button
            size="small"
            variant="contained"
            disabled={regionSubmitting}
            onClick={() => onApplyPolicyRegionCorrection(item, false)}
            sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
          >
            {reviewSubmittingKey === `policy-region-${item.reportId}-regions` ? "보정 중..." : "지역 보정"}
          </Button>
          <Button
            size="small"
            variant="outlined"
            disabled={regionSubmitting}
            onClick={() => onApplyPolicyRegionCorrection(item, true)}
            sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
          >
            {reviewSubmittingKey === `policy-region-${item.reportId}-nationwide` ? "처리 중..." : "전국 처리"}
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
}

export function PolicyFieldCorrectionPanel({
  item,
  reviewSubmittingKey,
  fieldInput,
  updatePolicyFieldCorrectionInput,
  onApplyPolicyFieldCorrection,
}) {
  return (
    <Box sx={{ p: 1.5, borderRadius: 1.5, border: `1px solid ${PANEL_LINE}`, bgcolor: "#f8fafc" }}>
      <Stack spacing={1}>
        <Typography sx={{ fontSize: 12, fontWeight: 800, color: INK2 }}>
          필드 보정
        </Typography>
        {item.reasonCode === "PERIOD_MISMATCH" && (
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
            <TextField
              size="small"
              type="date"
              label="신청 시작일"
              InputLabelProps={{ shrink: true }}
              value={fieldInput.applyStartDate ?? ""}
              onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { applyStartDate: event.target.value })}
              sx={{ flex: 1 }}
            />
            <TextField
              size="small"
              type="date"
              label="신청 종료일"
              InputLabelProps={{ shrink: true }}
              value={fieldInput.applyEndDate ?? ""}
              onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { applyEndDate: event.target.value })}
              sx={{ flex: 1 }}
            />
          </Stack>
        )}
        {item.reasonCode === "BROKEN_LINK" && (
          <TextField
            size="small"
            placeholder="새 원문 URL"
            value={fieldInput.detailUrl ?? ""}
            onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { detailUrl: event.target.value })}
          />
        )}
        {item.reasonCode === "ELIGIBILITY_MISMATCH" && (
          <TextField
            size="small"
            multiline
            minRows={2}
            placeholder="보정할 자격조건/설명"
            value={fieldInput.eligibilityText ?? ""}
            onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { eligibilityText: event.target.value })}
          />
        )}
        {item.reasonCode === "DUPLICATE_POLICY" && (
          <TextField
            size="small"
            type="number"
            placeholder="중복 기준 정책 ID"
            value={fieldInput.duplicateOfPolicyId ?? ""}
            onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { duplicateOfPolicyId: event.target.value })}
          />
        )}
        <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
          <TextField
            size="small"
            placeholder="보정 메모"
            value={fieldInput.correctionNote ?? ""}
            onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { correctionNote: event.target.value })}
            sx={{ flex: 1 }}
          />
          <Button
            size="small"
            variant="contained"
            disabled={reviewSubmittingKey === `policy-field-${item.reportId}`}
            onClick={() => onApplyPolicyFieldCorrection(item)}
            sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
          >
            {reviewSubmittingKey === `policy-field-${item.reportId}` ? "보정 중..." : "필드 보정"}
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
}

export function PolicyErrorReportReviewControls({
  item,
  isReviewed,
  reviewSubmittingKey,
  policyErrorReviewNotes,
  setPolicyErrorReviewNotes,
  onReviewPolicyErrorReport,
}) {
  return (
    <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
      <TextField
        size="small"
        placeholder="운영 메모 (선택)"
        value={policyErrorReviewNotes[item.reportId] ?? ""}
        onChange={(event) => setPolicyErrorReviewNotes((prev) => ({
          ...prev,
          [item.reportId]: event.target.value,
        }))}
        sx={{ flex: 1 }}
        disabled={isReviewed}
      />
      <Button
        size="small"
        variant="outlined"
        disabled={isReviewed || reviewSubmittingKey === `policy-${item.reportId}`}
        onClick={() => onReviewPolicyErrorReport(item.reportId)}
        sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
      >
        {isReviewed
          ? "처리완료됨"
          : reviewSubmittingKey === `policy-${item.reportId}` ? "처리 중..." : "처리완료"}
      </Button>
    </Stack>
  );
}
