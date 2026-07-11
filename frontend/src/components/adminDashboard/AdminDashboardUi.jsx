import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  Typography,
} from "@mui/material";
import {
  formatNumber,
  formatSourceType,
  formatStatusLabel,
} from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";
import {
  ACCENT,
  ACTIVE_CARD_SX,
  INFO_BORDER,
  INK,
  INK2,
  INK3,
  PANEL_BG,
  PANEL_LINE,
  REVIEW_GATE_TONE,
} from "./AdminDashboardUiTokens";

export function GateChip({ value }) {
  const tone = REVIEW_GATE_TONE[value] ?? { bg: "#eef2ff", border: "#c7d2fe", color: "#3730a3", label: formatStatusLabel(value) };
  return (
    <Chip
      label={tone.label}
      sx={{
        bgcolor: tone.bg,
        color: tone.color,
        border: `1px solid ${tone.border}`,
        fontWeight: 700,
        height: "auto",
        maxWidth: "100%",
        "& .MuiChip-label": {
          display: "block",
          whiteSpace: "normal",
          overflowWrap: "anywhere",
          wordBreak: "break-word",
          lineHeight: 1.25,
          py: 0.75,
        },
      }}
    />
  );
}

export function ToneChip({ toneMap, value }) {
  const tone = toneMap[value] ?? { label: formatStatusLabel(value), bg: "#f8fafc", border: PANEL_LINE, color: INK2 };
  return (
    <Chip
      label={tone.label}
      size="small"
      sx={{
        bgcolor: tone.bg,
        color: tone.color,
        border: `1px solid ${tone.border}`,
        fontWeight: 700,
      }}
    />
  );
}

export function AttentionNextAction({ nextAction }) {
  if (!nextAction) {
    return null;
  }

  return (
    <Box sx={{ p: 1, borderRadius: 1.5, bgcolor: "#f8fafc", border: `1px solid ${PANEL_LINE}` }}>
      <Typography sx={{ fontSize: 11, fontWeight: 800, color: INK3, textTransform: "uppercase", letterSpacing: "0.04em" }}>
        다음 조치
      </Typography>
      <Typography sx={{ fontSize: 12, color: INK2, mt: 0.35, overflowWrap: "anywhere", wordBreak: "break-word" }}>
        {nextAction}
      </Typography>
    </Box>
  );
}

export function MetricCard({
  title,
  value,
  description,
  descriptionColor = INK3,
  chip,
  actionLabel,
  onAction,
  focusTarget = false,
  cardProps,
  actionProps,
}) {
  return (
    <Card
      {...(focusTarget ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, "true") : {})}
      {...cardProps}
      sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)", ...ACTIVE_CARD_SX }}
    >
      <CardContent sx={{ p: 2.5 }}>
        <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <Box sx={{ minWidth: 0, width: "100%" }}>
            <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>{title}</Typography>
            <Typography
              sx={{
                fontSize: { xs: 22, sm: 28 },
                fontWeight: 800,
                color: INK,
                mt: 1,
                lineHeight: 1.15,
                overflowWrap: "anywhere",
                wordBreak: "break-word",
              }}
            >
              {value}
            </Typography>
            {description && (
              <Typography
                sx={{
                  fontSize: 12,
                  color: descriptionColor,
                  mt: 0.75,
                  overflowWrap: "anywhere",
                  wordBreak: "break-word",
                }}
              >
                {description}
              </Typography>
            )}
            {actionLabel && onAction && (
              <Button
                size="small"
                variant="text"
                {...actionProps}
                sx={{ mt: 1, px: 0, textTransform: "none", fontWeight: 700 }}
                onClick={onAction}
              >
                {actionLabel}
              </Button>
            )}
          </Box>
          {chip && <Box sx={{ width: { xs: "100%", sm: "auto" } }}>{chip}</Box>}
        </Stack>
      </CardContent>
    </Card>
  );
}

export function CohortMix({ mix }) {
  const items = [
    { label: "예제", value: mix?.exampleUsers },
    { label: "로컬 제한군", value: mix?.boundedLocalUsers },
    { label: "로컬 시드", value: mix?.localRealNonExampleSeedUsers },
    { label: "실사용자", value: mix?.realUserUsers },
  ];

  return (
    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
      {items.map((item) => (
        <Chip
          key={item.label}
          label={`${item.label} ${formatNumber(item.value)}`}
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
  );
}

export function ServiceListCard({ title, items, countLabel, focusTarget = false, cardProps }) {
  return (
    <Card
      {...(focusTarget ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, "true") : {})}
      {...cardProps}
      sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)", height: "100%", ...ACTIVE_CARD_SX }}
    >
      <CardContent sx={{ p: 2.5 }}>
        <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>{title}</Typography>
        <Stack spacing={1.5} mt={2}>
          {items?.length ? items.map((item) => (
            <Box key={`${title}-${item.serviceId}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
              <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={2}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontSize: 14, fontWeight: 700, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>{item.title}</Typography>
                  <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                    {formatSourceType(item.sourceType)} · {item.category}
                  </Typography>
                </Box>
                <Typography sx={{ fontSize: 14, fontWeight: 800, color: ACCENT, whiteSpace: "nowrap", alignSelf: { xs: "flex-start", sm: "flex-start" } }}>
                  {formatNumber(item[countLabel])}
                </Typography>
              </Stack>
              <Box mt={1.25}>
                <CohortMix mix={item.userMix} />
              </Box>
            </Box>
          )) : (
            <Typography sx={{ fontSize: 13, color: INK3 }}>표시할 데이터가 없습니다.</Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

export function CompactListCard({ title, description, items, renderItem, cardProps }) {
  return (
    <Card
      {...cardProps}
      sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)", height: "100%", ...ACTIVE_CARD_SX }}
    >
      <CardContent sx={{ p: 2.5 }}>
        <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>{title}</Typography>
        {description && <Typography sx={{ fontSize: 12, color: INK3, mt: 0.5 }}>{description}</Typography>}
        <Stack spacing={1.25} mt={2}>
          {items?.length ? items.map((item, index) => renderItem(item, index)) : (
            <Typography sx={{ fontSize: 13, color: INK3 }}>표시할 데이터가 없습니다.</Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

export function TriageSectionTitle({ eyebrow, title, description }) {
  return (
    <Box>
      <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
        {eyebrow}
      </Typography>
      <Typography sx={{ fontSize: 22, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
        {title}
      </Typography>
      <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
        {description}
      </Typography>
    </Box>
  );
}

export function QuickJumpButton({ label, targetId, count, subtle, onJump, tone = "info", focusKey = "default", preset = "quickJump", actionProps }) {
  return (
    <Button
      variant={subtle ? "outlined" : "contained"}
      {...actionProps}
      onClick={() => onJump?.(targetId, { tone, focusKey, preset })}
      sx={{
        borderRadius: 999,
        px: 2,
        py: 1,
        textTransform: "none",
        fontWeight: 800,
        bgcolor: subtle ? PANEL_BG : INK,
        color: subtle ? INK2 : "white",
        borderColor: subtle ? PANEL_LINE : INK,
        boxShadow: "none",
        "&:hover": {
          bgcolor: subtle ? "#f8fafc" : "#1f2937",
          borderColor: subtle ? "#cbd5e1" : "#1f2937",
          boxShadow: "none",
        },
      }}
    >
      {label}
      {typeof count === "number" ? ` · ${formatNumber(count)}` : ""}
    </Button>
  );
}

export function SectionStateCard({ title, description, action, children }) {
  return (
    <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)" }}>
      <CardContent sx={{ p: 2.5 }}>
        <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>{title}</Typography>
            {description && <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>{description}</Typography>}
          </Box>
          {action}
        </Stack>
        <Box mt={2}>{children}</Box>
      </CardContent>
    </Card>
  );
}

export function SectionLoadingCard({ title, description }) {
  return (
    <SectionStateCard title={title} description={description}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, py: 1 }}>
        <CircularProgress size={20} />
        <Typography sx={{ fontSize: 13, color: INK3 }}>데이터를 불러오는 중입니다.</Typography>
      </Box>
    </SectionStateCard>
  );
}

export function SectionErrorCard({ title, description, message, onRetry }) {
  return (
    <SectionStateCard
      title={title}
      description={description}
      action={(
        <Button variant="outlined" size="small" onClick={onRetry} sx={{ alignSelf: "flex-start" }}>
          다시 시도
        </Button>
      )}
    >
      <Alert severity="error">{message}</Alert>
    </SectionStateCard>
  );
}
