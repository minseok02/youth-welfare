#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-https://youthmoa.kr}"
WEB_INTERACTION_ROOT="${WEB_INTERACTION_ROOT:-${ROOT_DIR}/tmp/performance/web-interaction}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${WEB_INTERACTION_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
RUNNER_JS="${ARTIFACT_DIR}/web-interaction-runner.mjs"
RAW_JSON="${ARTIFACT_DIR}/web-interaction-raw.json"
SUMMARY_TXT="${ARTIFACT_DIR}/web-interaction-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/web-interaction-summary.json"
LATEST_DIR="${WEB_INTERACTION_ROOT}/latest"
LATEST_SUMMARY_TXT="${WEB_INTERACTION_ROOT}/latest-web-interaction-summary.txt"
LATEST_SUMMARY_JSON="${WEB_INTERACTION_ROOT}/latest-web-interaction-summary.json"

mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

if ! command -v node >/dev/null 2>&1; then
  {
    echo "web_interaction_baseline=skipped"
    echo "reason=node_unavailable"
  } | tee "${SUMMARY_TXT}"
  printf '{"status":"skipped","reason":"node_unavailable"}\n' > "${SUMMARY_JSON}"
  perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"
  exit 0
fi

if [[ ! -d "${ROOT_DIR}/frontend/node_modules/playwright" && ! -d "${ROOT_DIR}/frontend/node_modules/@playwright/test" ]]; then
  {
    echo "web_interaction_baseline=skipped"
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
const outputPath = process.argv[4];

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1366, height: 768 } });

async function preparePage(path) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.__interactionEvents = [];
    try {
      new PerformanceObserver((entryList) => {
        for (const entry of entryList.getEntries()) {
          window.__interactionEvents.push({
            name: entry.name,
            startTime: entry.startTime,
            duration: entry.duration,
            interactionId: entry.interactionId || 0,
          });
        }
      }).observe({ type: "event", buffered: true, durationThreshold: 0 });
    } catch {
      window.__interactionObserverUnavailable = true;
    }
  });
  const response = await page.goto(`${baseUrl}${path}`, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  return { page, status: response ? response.status() : null };
}

async function collect(page, scenario, status, actionStarted, error = null) {
  const actionEnded = Date.now();
  const events = await page.evaluate((startedEpoch) => {
    const nav = performance.getEntriesByType("navigation")[0];
    const originEpoch = nav ? performance.timeOrigin : Date.now() - performance.now();
    return (window.__interactionEvents || [])
      .filter((entry) => originEpoch + entry.startTime >= startedEpoch - 50)
      .slice(-40);
  }, actionStarted).catch(() => []);
  const maxEventDuration = events.reduce((max, entry) => Math.max(max, entry.duration || 0), 0);
  return {
    scenario,
    status,
    actionWall_ms: actionEnded - actionStarted,
    maxEventDuration_ms: maxEventDuration,
    eventCount: events.length,
    error,
  };
}

const results = [];

{
  const { page, status } = await preparePage("/policies");
  const started = Date.now();
  let error = null;
  try {
    const input = page.locator('input[placeholder*="정책명"]').first();
    await input.waitFor({ state: "visible", timeout: 10000 });
    await input.fill("청년");
    await input.press("Enter");
    await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(500);
  } catch (err) {
    error = err.name || "Error";
  }
  results.push(await collect(page, "policies_search_input_enter", status, started, error));
  await page.close();
}

{
  const { page, status } = await preparePage("/login");
  const started = Date.now();
  let error = null;
  try {
    await page.locator('input[placeholder="example@email.com"]').fill("perf.interaction@example.com");
    await page.locator('input[placeholder="비밀번호를 입력하세요"]').fill("Password123!");
    await page.waitForTimeout(250);
  } catch (err) {
    error = err.name || "Error";
  }
  results.push(await collect(page, "login_form_fill", status, started, error));
  await page.close();
}

await browser.close();
fs.writeFileSync(outputPath, `${JSON.stringify(results, null, 2)}\n`);
JS

(
  cd "${ROOT_DIR}/frontend"
  node "${RUNNER_JS}" "${ROOT_DIR}/frontend" "${FRONTEND_PUBLIC_BASE_URL}" "${RAW_JSON}"
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
payload = {"context": context, "status": "failed" if failed else "passed", "interactions": rows}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = [f"web_interaction_baseline={'failed' if failed else 'passed'}"]
for row in rows:
    lines.append(
        f"{row['scenario']} status={row.get('status')} action_wall_ms={row.get('actionWall_ms')} "
        f"max_event_duration_ms={row.get('maxEventDuration_ms')} event_count={row.get('eventCount')} error={row.get('error')}"
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
