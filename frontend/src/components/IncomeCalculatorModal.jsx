import { useState, useMemo } from "react";
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Box, Typography, Button, Select, MenuItem, FormControl,
  TextField, Chip, Divider, IconButton, InputAdornment,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import CalculateOutlinedIcon from "@mui/icons-material/CalculateOutlined";

// 2026년 기준 중위소득 (월, 원) — 보건복지부 고시
const MEDIAN_INCOME = {
  1: 2564238,
  2: 4199292,
  3: 5359036,
  4: 6494738,
  5: 7556719,
  6: 8555952,
};

const TIERS = [
  { maxPct: 50,       value: "1", label: "기초생활수급자",         desc: "중위소득 50% 이하" },
  { maxPct: 100,      value: "3", label: "차상위계층",             desc: "중위소득 50~100%" },
  { maxPct: 150,      value: "5", label: "소득 하위 50% 이하",     desc: "중위소득 100~150%" },
  { maxPct: 200,      value: "7", label: "소득 중간 (50~100%)",   desc: "중위소득 150~200%" },
  { maxPct: Infinity, value: "9", label: "소득 상위 (100% 초과)", desc: "중위소득 200% 초과" },
];

export default function IncomeCalculatorModal({ open, onClose, onSelect }) {
  const [household, setHousehold] = useState(1);
  const [monthlyIncome, setMonthlyIncome] = useState("");

  const result = useMemo(() => {
    const val = parseFloat(monthlyIncome);
    if (!monthlyIncome || isNaN(val) || val < 0) return null;
    const base = MEDIAN_INCOME[Math.min(household, 6)];
    const pct = (val * 10000 / base) * 100;
    const tier = TIERS.find((t) => pct <= t.maxPct);
    return { pct: Math.round(pct), tier };
  }, [household, monthlyIncome]);

  const handleClose = () => {
    setHousehold(1);
    setMonthlyIncome("");
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", pb: 1 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <CalculateOutlinedIcon color="primary" fontSize="small" />
          <Typography fontWeight={700}>소득분위 계산기</Typography>
        </Box>
        <IconButton size="small" onClick={handleClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ pt: 1 }}>
        <Typography variant="body2" color="text.secondary" mb={2.5}>
          가구원 수와 월 소득을 입력하면 해당 소득분위를 알려드려요.
        </Typography>

        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <Box>
            <Typography variant="caption" fontWeight={700} mb={0.5} display="block">가구원 수</Typography>
            <FormControl size="small" fullWidth>
              <Select value={household} onChange={(e) => setHousehold(e.target.value)}>
                {[1, 2, 3, 4, 5].map((n) => (
                  <MenuItem key={n} value={n}>{n}인 가구</MenuItem>
                ))}
                <MenuItem value={6}>6인 이상 가구</MenuItem>
              </Select>
            </FormControl>
          </Box>

          <Box>
            <Typography variant="caption" fontWeight={700} mb={0.5} display="block">
              월 소득 (세전, 가구 전체)
            </Typography>
            <TextField
              size="small"
              fullWidth
              type="number"
              value={monthlyIncome}
              onChange={(e) => setMonthlyIncome(e.target.value)}
              placeholder="예: 200"
              InputProps={{
                endAdornment: <InputAdornment position="end">만원</InputAdornment>,
              }}
              inputProps={{ min: 0 }}
            />
            <Typography variant="caption" color="text.disabled" mt={0.5} display="block">
              2026년 기준 중위소득 · {household}인 가구: {Math.round(MEDIAN_INCOME[Math.min(household, 6)] / 10000)}만원/월
            </Typography>
          </Box>

          {result && (
            <>
              <Divider />
              <Box sx={{ textAlign: "center", py: 1 }}>
                <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>
                  기준 중위소득 대비
                </Typography>
                <Typography variant="h4" fontWeight={800} color="primary" mb={1.5}>
                  {result.pct}%
                </Typography>
                <Chip
                  label={result.tier.label}
                  color="primary"
                  sx={{ fontWeight: 700, mb: 0.75, px: 1 }}
                />
                <Typography variant="caption" color="text.secondary" display="block">
                  {result.tier.desc}
                </Typography>
              </Box>
            </>
          )}
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
        <Button onClick={handleClose} variant="outlined" size="small" sx={{ flex: 1 }}>
          닫기
        </Button>
        <Button
          onClick={() => { onSelect(result.tier.value); handleClose(); }}
          variant="contained"
          size="small"
          disabled={!result}
          sx={{ flex: 2 }}
        >
          이 구간으로 선택하기
        </Button>
      </DialogActions>
    </Dialog>
  );
}
