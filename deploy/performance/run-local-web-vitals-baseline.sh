#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-https://youthmoa.kr}"
WEB_ROUTES="${WEB_ROUTES:-/ /policies /policies?keyword=%EC%B2%AD%EB%85%84 /login}"
WEB_VITALS_ROOT="${WEB_VITALS_ROOT:-${ROOT_DIR}/tmp/performance/web-vitals}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${WEB_VITALS_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
RUNNER_JS="${ARTIFACT_DIR}/web-vitals-runner.mjs"
RAW_JSON="${ARTIFACT_DIR}/web-vitals-raw.json"
SUMMARY_TXT="${ARTIFACT_DIR}/web-vitals-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/web-vitals-summary.json"
LATEST_DIR="${WEB_VITALS_ROOT}/latest"
LATEST_SUMMARY_TXT="${WEB_VITALS_ROOT}/latest-web-vitals-summary.txt"
LATEST_SUMMARY_JSON="${WEB_VITALS_ROOT}/latest-web-vitals-summary.json"

mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

if ! command -v node >/dev/null 2>&1; then
  {
    echo "web_vitals_baseline=skipped"
    echo "reason=node_unavailable"
  } | tee "${SUMMARY_TXT}"
  printf '{"status":"skipped","reason":"node_unavailable"}\n' > "${SUMMARY_JSON}"
  perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"
  exit 0
fi

if [[ ! -d "${ROOT_DIR}/frontend/node_modules/playwright" && ! -d "${ROOT_DIR}/frontend/node_modules/@playwright/test" ]]; then
  {
    echo "web_vitals_baseline=skipped"
    echo "reason=playwright_node_modules_unavailable"
  } | tee "${SUMMARY_TXT}"
  printf '{"status":"skipped","reason":"playwright_node_modules_unavailable"}\n' > "${SUMMARY_JSON}"
  perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"
  exit 0
fi

cat > "${RUNNER_JS}" <<'JS'
import { createRequire } from "node:module";
import fs from "node:fs";

const frontendDir = process.argv[2];
const require = createRequire(`${frontendDir}/package.json`);
const { chromium } = require("playwright");
const baseUrl = process.argv[3].replace(/\/$/, "");
const routes = process.argv[4].split(/\s+/).filter(Boolean);
const outputPath = process.argv[5];

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 1366, height: 768 },
});

const results = [];
for (const route of routes) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.__perf = { lcp: null, cls: 0 };
    try {
      new PerformanceObserver((entryList) => {
        const entries = entryList.getEntries();
        const last = entries[entries.length - 1];
        if (last) {
          window.__perf.lcp = last.startTime;
        }
      }).observe({ type: "largest-contentful-paint", buffered: true });
      new PerformanceObserver((entryList) => {
        for (const entry of entryList.getEntries()) {
          if (!entry.hadRecentInput) {
            window.__perf.cls += entry.value;
          }
        }
      }).observe({ type: "layout-shift", buffered: true });
    } catch {
      window.__perf.observerUnavailable = true;
    }
  });

  const url = `${baseUrl}${route.startsWith("/") ? route : `/${route}`}`;
  const started = Date.now();
  let status = null;
  let error = null;
  try {
    const response = await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });
    status = response ? response.status() : null;
    await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(1500);
  } catch (err) {
    error = err.name || "Error";
  }

  const metrics = await page.evaluate(() => {
    const nav = performance.getEntriesByType("navigation")[0];
    const paints = Object.fromEntries(
      performance.getEntriesByType("paint").map((entry) => [entry.name, entry.startTime])
    );
    const resources = performance.getEntriesByType("resource");
    const transferSize = resources.reduce((sum, entry) => sum + (entry.transferSize || 0), 0);
    return {
      domContentLoaded_ms: nav ? nav.domContentLoadedEventEnd : null,
      loadEventEnd_ms: nav ? nav.loadEventEnd : null,
      responseEnd_ms: nav ? nav.responseEnd : null,
      firstPaint_ms: paints["first-paint"] ?? null,
      firstContentfulPaint_ms: paints["first-contentful-paint"] ?? null,
      largestContentfulPaint_ms: window.__perf?.lcp ?? null,
      cumulativeLayoutShift: window.__perf?.cls ?? null,
      interactionToNextPaint_ms: null,
      resourceCount: resources.length,
      transferSize_bytes: transferSize,
    };
  }).catch(() => ({}));

  results.push({
    route,
    url,
    status,
    error,
    wallClock_ms: Date.now() - started,
    ...metrics,
  });
  await page.close();
}

await browser.close();
fs.writeFileSync(outputPath, `${JSON.stringify(results, null, 2)}\n`);
JS

(
  cd "${ROOT_DIR}/frontend"
  node "${RUNNER_JS}" "${ROOT_DIR}/frontend" "${FRONTEND_PUBLIC_BASE_URL}" "${WEB_ROUTES}" "${RAW_JSON}"
)

python3 - "${RAW_JSON}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${CONTEXT_TXT}" <<'PY'
import json
import sys
from pathlib import Path

raw_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:5])
rows = json.loads(raw_path.read_text(encoding="utf-8"))
context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

failed = [row for row in rows if row.get("error") or not (row.get("status") and 200 <= int(row["status"]) < 400)]
payload = {
    "context": context,
    "status": "failed" if failed else "passed",
    "routes": rows,
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = [f"web_vitals_baseline={'failed' if failed else 'passed'}"]
for row in rows:
    lines.append(
        f"{row['route']} status={row.get('status')} wall_ms={row.get('wallClock_ms')} "
        f"fcp_ms={row.get('firstContentfulPaint_ms')} lcp_ms={row.get('largestContentfulPaint_ms')} "
        f"cls={row.get('cumulativeLayoutShift')} inp_ms={row.get('interactionToNextPaint_ms')} "
        f"resources={row.get('resourceCount')} transfer_bytes={row.get('transferSize_bytes')} "
        f"error={row.get('error')}"
    )
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
if failed:
    raise SystemExit(1)
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
