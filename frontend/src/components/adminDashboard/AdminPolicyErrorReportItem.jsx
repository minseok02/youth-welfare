import { Box, Stack } from "@mui/material";
import { PANEL_LINE } from "./AdminDashboardUiTokens";
import {
  PolicyErrorReportHeader,
  PolicyErrorReportReporterNote,
  PolicyErrorReportReviewControls,
  PolicyErrorReportReviewStatus,
  PolicyFieldCorrectionPanel,
  PolicyRegionCorrectionPanel,
} from "./AdminPolicyErrorReportPanels";
import { resolveFieldCorrectionType } from "../../lib/adminDashboardDisplay";

export default function AdminPolicyErrorReportItem({
  item,
  regionOptionsQuery,
  regionOptions,
  reviewSubmittingKey,
  policyRegionCorrectionInputs,
  updatePolicyRegionCorrectionInput,
  policyFieldCorrectionInputs,
  updatePolicyFieldCorrectionInput,
  policyErrorReviewNotes,
  setPolicyErrorReviewNotes,
  onApplySuggestedRegionCorrection,
  onApplyPolicyRegionCorrection,
  onApplyPolicyFieldCorrection,
  onReviewPolicyErrorReport,
}) {
  const regionInput = policyRegionCorrectionInputs[item.reportId] ?? {};
  const fieldInput = policyFieldCorrectionInputs[item.reportId] ?? {};
  const isReviewed = item.status === "REVIEWED";

  return (
    <Box
      key={item.reportId}
      sx={{ p: 1.75, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
    >
      <Stack spacing={1}>
        <PolicyErrorReportHeader item={item} />
        <PolicyErrorReportReporterNote item={item} />
        {isReviewed && <PolicyErrorReportReviewStatus item={item} />}
        {item.reasonCode === "REGION_MISMATCH" && !isReviewed && (
          <PolicyRegionCorrectionPanel
            item={item}
            regionOptionsQuery={regionOptionsQuery}
            regionOptions={regionOptions}
            reviewSubmittingKey={reviewSubmittingKey}
            regionInput={regionInput}
            updatePolicyRegionCorrectionInput={updatePolicyRegionCorrectionInput}
            onApplySuggestedRegionCorrection={onApplySuggestedRegionCorrection}
            onApplyPolicyRegionCorrection={onApplyPolicyRegionCorrection}
          />
        )}
        {resolveFieldCorrectionType(item.reasonCode) && !isReviewed && (
          <PolicyFieldCorrectionPanel
            item={item}
            reviewSubmittingKey={reviewSubmittingKey}
            fieldInput={fieldInput}
            updatePolicyFieldCorrectionInput={updatePolicyFieldCorrectionInput}
            onApplyPolicyFieldCorrection={onApplyPolicyFieldCorrection}
          />
        )}
        <PolicyErrorReportReviewControls
          item={item}
          isReviewed={isReviewed}
          reviewSubmittingKey={reviewSubmittingKey}
          policyErrorReviewNotes={policyErrorReviewNotes}
          setPolicyErrorReviewNotes={setPolicyErrorReviewNotes}
          onReviewPolicyErrorReport={onReviewPolicyErrorReport}
        />
      </Stack>
    </Box>
  );
}
