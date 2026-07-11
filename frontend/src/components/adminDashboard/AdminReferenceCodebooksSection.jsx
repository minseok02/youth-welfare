import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
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
  INK2,
  INK3,
  PANEL_BG,
  PANEL_LINE,
} from "./AdminDashboardUiTokens";
import { formatNumber } from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_CARD_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export default function AdminReferenceCodebooksSection({
  officialCodebooksQuery,
  officialCodebooksErrorMessage,
  officialCodebooks,
  effectiveSelectedCodeSetKey,
  onSelectedCodeSetKeyChange,
  codebookQueryText,
  onCodebookQueryTextChange,
  selectedCodebookSummary,
  officialCodebookDetailQuery,
  officialCodebookDetailErrorMessage,
  officialCodebookDetail,
  detailRows,
}) {
  return (
    <Box id="admin-reference-codebooks" sx={{ scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                기준 정보
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                공식 코드북 탐색
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                사용자 제공 표준 코드북을 운영 화면에서 바로 조회합니다. 작은 코드표는 검색 결과를, 큰 자료는 메타데이터와 샘플 행을 보여줍니다.
              </Typography>
            </Box>

            {officialCodebooksQuery.isLoading && (
              <SectionLoadingCard
                title="공식 코드북 로딩 중"
                description="코드셋 목록을 불러오는 중입니다."
              />
            )}

            {officialCodebooksQuery.isError && (
              <SectionErrorCard
                title="공식 코드북 목록 로드 실패"
                description="기준정보 API를 불러오지 못했습니다."
                message={officialCodebooksErrorMessage}
                onRetry={() => officialCodebooksQuery.refetch()}
              />
            )}

            {!officialCodebooksQuery.isLoading && !officialCodebooksQuery.isError && (
              <>
                <Stack direction={{ xs: "column", lg: "row" }} spacing={2}>
                  <Stack spacing={0.75} sx={{ minWidth: { xs: "100%", lg: 320 } }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>코드셋 선택</Typography>
                    <Select
                      size="small"
                      value={effectiveSelectedCodeSetKey}
                      onChange={(event) => onSelectedCodeSetKeyChange(event.target.value)}
                      sx={{ bgcolor: PANEL_BG }}
                    >
                      {officialCodebooks.map((item) => (
                        <MenuItem key={item.codeSetKey} value={item.codeSetKey}>
                          {item.codeSetKey}
                        </MenuItem>
                      ))}
                    </Select>
                  </Stack>
                  <Stack spacing={0.75} sx={{ flex: 1 }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>검색</Typography>
                    <TextField
                      size="small"
                      placeholder="코드값, 라벨, 설명 검색"
                      value={codebookQueryText}
                      onChange={(event) => onCodebookQueryTextChange(event.target.value)}
                    />
                  </Stack>
                </Stack>

                {selectedCodebookSummary && (
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip label={selectedCodebookSummary.sourceType} size="small" />
                    <Chip label={`${selectedCodebookSummary.rowCount.toLocaleString()} rows`} size="small" variant="outlined" />
                    <Chip label={selectedCodebookSummary.rowDataIncluded ? "행 조회 가능" : "메타데이터 전용"} size="small" variant="outlined" />
                    <Chip label={selectedCodebookSummary.sourceFile} size="small" variant="outlined" />
                  </Stack>
                )}

                {officialCodebookDetailQuery.isLoading && (
                  <SectionLoadingCard
                    title="코드북 상세 로딩 중"
                    description="선택한 코드셋의 샘플 행을 불러오는 중입니다."
                  />
                )}

                {officialCodebookDetailQuery.isError && (
                  <SectionErrorCard
                    title="코드북 상세 로드 실패"
                    description="선택한 코드셋의 내용을 불러오지 못했습니다."
                    message={officialCodebookDetailErrorMessage}
                    onRetry={() => officialCodebookDetailQuery.refetch()}
                  />
                )}

                {officialCodebookDetail && !officialCodebookDetailQuery.isLoading && !officialCodebookDetailQuery.isError && (
                  <Stack spacing={2}>
                    <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" } }}>
                      <MetricCard
                        title="전체 행 수"
                        value={formatNumber(officialCodebookDetail.rowCount)}
                        description={officialCodebookDetail.sheetName ?? "원본 시트 정보 없음"}
                      />
                      <MetricCard
                        title="표시 행 수"
                        value={formatNumber(officialCodebookDetail.matchedRowCount)}
                        description={codebookQueryText.trim() ? "검색 조건 반영" : "기본 조회"}
                        focusTarget
                        cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, ADMIN_DASHBOARD_CARD_KEYS.matchedRowCount)}
                      />
                      <MetricCard
                        title="용도"
                        value={officialCodebookDetail.intendedUse ?? "-"}
                        description={officialCodebookDetail.sourceFile}
                      />
                    </Box>

                    {officialCodebookDetail.metadata && (
                      <Stack spacing={1}>
                        <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK }}>메타데이터</Typography>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          {Object.entries(officialCodebookDetail.metadata)
                            .filter(([key]) => key !== "sampleRows")
                            .map(([key, value]) => (
                              <Chip
                                key={key}
                                label={`${key}: ${Array.isArray(value) ? value.join(", ") : String(value)}`}
                                size="small"
                                variant="outlined"
                              />
                            ))}
                        </Stack>
                      </Stack>
                    )}

                    {detailRows.length > 0 ? (
                      <TableContainer sx={{ border: `1px solid ${PANEL_LINE}`, borderRadius: 3, overflow: "hidden" }}>
                        <Table size="small">
                          <TableHead sx={{ bgcolor: "#f8fafc" }}>
                            <TableRow>
                              {(officialCodebookDetail.headers ?? Object.keys(detailRows[0] ?? {})).map((header) => (
                                <TableCell key={header} sx={{ fontWeight: 800, color: INK2 }}>{header}</TableCell>
                              ))}
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {detailRows.map((row, index) => (
                              <TableRow key={`${effectiveSelectedCodeSetKey}-${index}`}>
                                {(officialCodebookDetail.headers ?? Object.keys(row ?? {})).map((header) => (
                                  <TableCell key={header} sx={{ color: INK, verticalAlign: "top" }}>
                                    {row?.[header] ?? "-"}
                                  </TableCell>
                                ))}
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    ) : (
                      <Alert severity="info">표시할 행이 없습니다.</Alert>
                    )}
                  </Stack>
                )}
              </>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
