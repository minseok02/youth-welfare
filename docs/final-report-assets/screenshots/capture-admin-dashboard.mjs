import playwright from "../../../frontend/node_modules/playwright/index.js";
import {
  adminDashboardFixtures,
  buildAdminDashboardApiPayload,
  buildOfficialCodebooksApiPayload,
  officialCodebookRows,
} from "../../../frontend/e2e/fixtures/adminDashboard.js";
import path from "node:path";
import { fileURLToPath } from "node:url";

const { chromium } = playwright;

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const appBaseUrl = process.env.REPORT_SCREENSHOT_APP_URL || "http://127.0.0.1:5173";
const email = "admin@example.com";

const base64UrlJson = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");

function buildTestAccessToken(roles = ["ROLE_ADMIN"]) {
  const nowSeconds = Math.floor(Date.now() / 1000);
  return [
    base64UrlJson({ alg: "none", typ: "JWT" }),
    base64UrlJson({
      sub: "report-admin-key",
      uid: 1,
      roles,
      authorities: roles,
      iat: nowSeconds,
      exp: nowSeconds + 1800,
      iatm: nowSeconds * 1000,
    }),
    "report-signature",
  ].join(".");
}

async function mockAuth(page) {
  const accessToken = buildTestAccessToken();
  await page.route("**/api/auth/login", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify({ success: true, data: { accessToken, refreshToken: "mock-refresh-token" } }),
  }));
  await page.route("**/api/auth/refresh", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify({ success: true, data: { accessToken, refreshToken: "mock-refresh-token" } }),
  }));
  await page.route("**/api/users/me", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify({
      success: true,
      data: {
        email,
        name: "관리자",
        priorities: ["HOUSING"],
        roles: ["ROLE_ADMIN"],
        isAdmin: true,
      },
    }),
  }));
  await page.route("**/api/alerts*", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify({ success: true, data: { content: [], totalElements: 0 } }),
  }));
}

async function mockAdminDashboardApis(page) {
  const routes = [
    ["**/api/admin/dashboard/summary*", adminDashboardFixtures.summary],
    ["**/api/admin/dashboard/recommendation-breakdowns*", adminDashboardFixtures.breakdowns],
    ["**/api/admin/dashboard/recommendation-run-summary*", adminDashboardFixtures.recommendationRunSummary],
    ["**/api/admin/dashboard/collect-failures*", adminDashboardFixtures.collectFailures],
    ["**/api/admin/dashboard/search-failures*", adminDashboardFixtures.searchFailures],
    ["**/api/admin/dashboard/user-profile-standard-code-coverage*", adminDashboardFixtures.userProfileStandardCodeCoverage],
    ["**/api/admin/dashboard/attention-feed*", adminDashboardFixtures.attentionFeed],
    ["**/api/admin/dashboard/standard-code-effect-observation*", adminDashboardFixtures.standardCodeEffectObservation],
    ["**/api/admin/dashboard/wrapper-observation*", adminDashboardFixtures.wrapperObservation],
    ["**/api/admin/dashboard/policy-error-reports*", adminDashboardFixtures.policyErrorReports],
    ["**/api/admin/dashboard/region-options*", adminDashboardFixtures.regionOptions],
    ["**/api/admin/dashboard/policy-region-corrections*", adminDashboardFixtures.policyRegionCorrections],
    ["**/api/admin/dashboard/policy-field-corrections*", adminDashboardFixtures.policyFieldCorrections],
    ["**/api/admin/dashboard/support-inquiries*", adminDashboardFixtures.supportInquiries],
    ["**/api/admin/dashboard/policy-duplicate-groups*", adminDashboardFixtures.policyDuplicateGroups],
    ["**/api/admin/dashboard/policy-link-reviews*", adminDashboardFixtures.policyLinkReviews],
    ["**/api/admin/dashboard/notification-attempt-summary*", adminDashboardFixtures.notificationAttemptSummary],
  ];

  await Promise.all(routes.map(([url, data]) => page.route(url, (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify(buildAdminDashboardApiPayload(data)),
  }))));

  await page.route("**/api/admin/dashboard/notification-stale-targets*", (route) => {
    const url = new URL(route.request().url());
    const olderThanDays = Number(url.searchParams.get("olderThanDays") || "14");
    const payload = olderThanDays === 7
      ? adminDashboardFixtures.notificationStaleTargets7d
      : adminDashboardFixtures.notificationStaleTargets;
    return route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify(buildAdminDashboardApiPayload(payload)),
    });
  });

  await page.route("**/api/reference/official-codes", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify(buildOfficialCodebooksApiPayload(adminDashboardFixtures.officialCodebooks)),
  }));
  await page.route("**/api/reference/official-codes/LOCAL_HOUSING_TYPE*", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify(buildOfficialCodebooksApiPayload({
      codeSetKey: "LOCAL_HOUSING_TYPE",
      sourceType: "xlsx",
      sourceFile: "주택유형구분코드 조회자료.xlsx",
      intendedUse: "user-profile and housing policy normalization",
      rowCount: officialCodebookRows.LOCAL_HOUSING_TYPE.length,
      matchedRowCount: officialCodebookRows.LOCAL_HOUSING_TYPE.length,
      sheetName: "주택유형구분코드 조회자료",
      headers: ["코드값", "코드값의미", "비고"],
      rows: officialCodebookRows.LOCAL_HOUSING_TYPE,
    })),
  }));
  await page.route("**/api/reference/official-codes/LOCAL_AGENCY_CODES*", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify(buildOfficialCodebooksApiPayload({
      codeSetKey: "LOCAL_AGENCY_CODES",
      sourceType: "txt",
      sourceFile: "기관코드 전체자료(유형분류 의미추가).txt",
      intendedUse: "gov24 agency normalization crosswalk",
      rowCount: 135186,
      matchedRowCount: 5,
      metadata: {
        activeRowCount: 135186,
        topLevelAgencyCount: 219,
        sampleRows: [{ 기관코드: "1741000", 전체기관명: "행정안전부", 유형분류_중_의미: "중앙행정기관" }],
      },
    })),
  }));
}

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({
  viewport: { width: 1440, height: 1050 },
  deviceScaleFactor: 1,
});

await mockAuth(page);
await mockAdminDashboardApis(page);

await page.goto(`${appBaseUrl}/admin/dashboard`, { waitUntil: "domcontentloaded" });
await page.getByText("운영 추천 대시보드").waitFor({ timeout: 30_000 });
await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
await page.addStyleTag({
  content: "*,*::before,*::after{transition-duration:0s!important;animation-duration:0s!important}",
});
await page.waitForTimeout(1_000);
await page.screenshot({ path: path.join(__dirname, "screen-15-admin-dashboard.png") });
await browser.close();

console.log("wrote screen-15-admin-dashboard.png");
