import { expect, test } from "@playwright/test";
import { createHash, randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import {
  adminDashboardFixtures,
  buildAdminDashboardApiPayload,
  buildOfficialCodebooksApiPayload,
  officialCodebookRows,
} from "./fixtures/adminDashboard.js";
import { expectLoggedInChat, loginFromProtectedRoute, loginThroughForm } from "./support/auth.js";
import {
  adminAction,
  activePrimaryFocusWithin,
  attentionAction,
  activeCard,
  activeFocus,
  activeFocusWithin,
  activeList,
  activeSection,
  attentionPrimaryAction,
  section,
} from "./support/adminDashboardSelectors.js";
import { resolveAdminCredentials, resolveUserCredentials } from "./support/env.js";
import {
  ADMIN_DASHBOARD_ACTION_KEYS,
  ADMIN_DASHBOARD_ATTENTION_KEYS,
  ADMIN_DASHBOARD_CARD_KEYS,
  ADMIN_DASHBOARD_FOCUS_KEYS,
  ADMIN_DASHBOARD_LIST_KEYS,
} from "../src/lib/adminDashboardTestHooks.js";

const userCredentials = resolveUserCredentials();
const adminCredentials = resolveAdminCredentials();
const apiBaseUrl = process.env.VITE_API_BASE_URL || "http://127.0.0.1:8082";
const adminDashboardE2EFailureStorageKey = "__ADMIN_DASHBOARD_E2E_FAIL__";

test.setTimeout(90_000);

const policyDetailUrl = (policyId) => new RegExp(`/policies/${policyId}(?:\\?.*)?$`);

function base64UrlJson(value) {
  return Buffer.from(JSON.stringify(value))
    .toString("base64url");
}

function buildTestAccessToken(roles = ["ROLE_USER"]) {
  const nowSeconds = Math.floor(Date.now() / 1000);
  return [
    base64UrlJson({ alg: "none", typ: "JWT" }),
    base64UrlJson({
      sub: "playwright-user-key",
      uid: 1,
      roles,
      authorities: roles,
      iat: nowSeconds,
      exp: nowSeconds + 1800,
      iatm: nowSeconds * 1000,
    }),
    "test-signature",
  ].join(".");
}

async function mockLoginApis(page, {
  roles = ["ROLE_USER"],
  email = userCredentials.email,
  name = "테스트사용자",
} = {}) {
  await page.route("**/api/auth/login", async (route) => {
    if (route.request().method() !== "POST") {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          accessToken: buildTestAccessToken(roles),
          refreshToken: "mock-refresh-token",
        },
      }),
    });
  });

  await page.route("**/api/users/me", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          email,
          name,
          priorities: ["HOUSING"],
          roles,
          isAdmin: roles.includes("ROLE_ADMIN"),
        },
      }),
    });
  });
}

async function mockAuthRefreshApi(page, {
  roles = ["ROLE_USER"],
} = {}) {
  await page.route("**/api/auth/refresh", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          accessToken: buildTestAccessToken(roles),
          refreshToken: "mock-refresh-token",
        },
      }),
    });
  });
}

async function waitForProgressToSettle(page) {
  await expect(page.getByRole("progressbar")).toHaveCount(0, { timeout: 15_000 });
}

async function expectPolicyListItemReady(page, policy) {
  const item = page.getByTestId(`policy-result-${policy.id}`).first();
  try {
    await expect(item).toBeVisible({ timeout: 3_000 });
    await expect(item.getByText(policy.title, { exact: true })).toBeVisible();
    return item;
  } catch {
    const title = page.getByText(policy.title, { exact: true }).first();
    await expect(title).toBeVisible({ timeout: 15_000 });
    return title;
  }
}

async function expectPolicyDetailReady(page, policy, { requireErrorReport = true } = {}) {
  await expect(page).toHaveURL(policyDetailUrl(policy.id), { timeout: 15_000 });
  await waitForProgressToSettle(page);
  await expect(page.getByText(policy.title, { exact: true }).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText("요약 정보", { exact: true })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole("button", { name: /뒤로가기/ })).toBeVisible({ timeout: 15_000 });
  if (requireErrorReport) {
    await expect(page.getByRole("button", { name: "정책 오류 제보", exact: true })).toBeVisible({ timeout: 15_000 });
  }
}

async function openPolicyFromList(page, policy) {
  const item = await expectPolicyListItemReady(page, policy);
  await Promise.all([
    page.waitForURL(policyDetailUrl(policy.id), { timeout: 15_000 }),
    item.click(),
  ]);
  await expectPolicyDetailReady(page, policy);
}

async function clickAndWaitForUrl(locator, page, expectedUrl) {
  await expect(locator).toBeVisible({ timeout: 15_000 });
  await expect(locator).toBeEnabled();
  await locator.scrollIntoViewIfNeeded();
  await Promise.all([
    page.waitForURL(expectedUrl, { timeout: 15_000 }),
    locator.click(),
  ]);
}

async function expectBookmarkTabReady(page, policy = null) {
  await expect(page).toHaveURL(/\/mypage\?tab=2$/, { timeout: 15_000 });
  await waitForProgressToSettle(page);
  await expect(page.getByText("북마크한 정책")).toBeVisible({ timeout: 15_000 });
  if (policy) {
    const bookmark = page.getByTestId(`bookmark-policy-${policy.id}`).first();
    try {
      await expect(bookmark).toBeVisible({ timeout: 3_000 });
      await expect(bookmark.getByText(policy.title, { exact: true })).toBeVisible();
      return bookmark;
    } catch {
      const title = page.getByText(policy.title, { exact: true }).first();
      await expect(title).toBeVisible({ timeout: 15_000 });
      return title;
    }
  }
  return null;
}

async function searchPolicies(page, keyword) {
  await page.goto("/policies");
  await waitForProgressToSettle(page);
  await page.getByPlaceholder("정책명, 키워드를 검색해보세요 (예: 월세, 창업)").fill(keyword);
  await page.getByRole("button", { name: "검색", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/policies\\?[^#]*search=[^#]*${encodeURIComponent(keyword)}`));
  await waitForProgressToSettle(page);
}

async function fetchFirstSearchResult(request, keyword) {
  const response = await request.get(`${apiBaseUrl}/api/policies/search`, {
    params: {
      keyword,
      page: "0",
      size: "10",
      sort: "RELEVANCE",
      statusFilter: "ACTIVE_ONLY",
    },
  });
  expect(response.ok()).toBeTruthy();
  const payload = await response.json();
  const firstPolicy = payload?.data?.content?.[0];
  expect(firstPolicy?.title).toBeTruthy();
  return firstPolicy;
}

async function openFirstSearchResult(page, request, keyword) {
  await searchPolicies(page, keyword);
  const currentSearch = new URL(page.url()).searchParams.get("search") || keyword;
  const firstPolicy = await fetchFirstSearchResult(request, currentSearch);
  await openPolicyFromList(page, firstPolicy);
  return firstPolicy;
}

async function ensurePolicyBookmarked(request, credentials, policyId) {
  return ensurePolicyBookmarkState(request, credentials, policyId, true);
}

function seedVerifiedEmail(email) {
  execFileSync("bash", ["./scripts/seed-verified-email.sh", email], {
    cwd: process.cwd(),
    stdio: "ignore",
  });
}

function sha256Hex(value) {
  return createHash("sha256").update(String(value).trim().toLowerCase()).digest("hex");
}

function readJwtPayload(token) {
  const [, payloadPart] = String(token || "").split(".");
  expect(payloadPart).toBeTruthy();
  return JSON.parse(Buffer.from(payloadPart, "base64url").toString("utf8"));
}

function issueDirectPasswordResetTokenForUserKey(userKey) {
  const token = randomUUID();
  const tokenHash = sha256Hex(token);
  const userKeyHash = sha256Hex(userKey);
  const ttlSeconds = process.env.PASSWORD_RESET_TTL_SECONDS || "1800";
  const redisContainerName = process.env.REDIS_CONTAINER_NAME || "youth-welfare-redis";
  const userTokenKey = `password-reset:user:v2:${userKeyHash}`;

  const previousTokenHash = execFileSync("docker", [
    "exec",
    redisContainerName,
    "redis-cli",
    "GET",
    userTokenKey,
  ], {
    encoding: "utf8",
  }).trim();

  if (previousTokenHash) {
    execFileSync("docker", [
      "exec",
      redisContainerName,
      "redis-cli",
      "DEL",
      `password-reset:${previousTokenHash}`,
      userTokenKey,
      `password-reset:user:${userKey}`,
    ], {
      stdio: "ignore",
    });
  }

  execFileSync("docker", [
    "exec",
    redisContainerName,
    "redis-cli",
    "SETEX",
    `password-reset:${tokenHash}`,
    ttlSeconds,
    userKey,
  ], {
    stdio: "ignore",
  });
  execFileSync("docker", [
    "exec",
    redisContainerName,
    "redis-cli",
    "SETEX",
    userTokenKey,
    ttlSeconds,
    tokenHash,
  ], {
    stdio: "ignore",
  });

  return token;
}

async function signupUser(request, { email, password, name = "리셋테스트" }) {
  seedVerifiedEmail(email);
  const response = await request.post(`${apiBaseUrl}/api/auth/signup`, {
    data: {
      email,
      password,
      name,
      birthDate: "1999-02-10",
      privacyNoticeConfirmed: true,
      optionalProfileConsentAgreed: true,
      sido: "서울특별시",
      sgg: "중구",
      incomeLevel: 5,
      employmentStatus: "미취업",
      householdType: "1인 가구",
    },
  });
  expect(response.ok()).toBeTruthy();
}

async function issuePasswordReset(request, email, currentPassword) {
  await request.post(`${apiBaseUrl}/api/auth/password-reset/request`, {
    data: { email },
  });

  const loginResponse = await request.post(`${apiBaseUrl}/api/auth/login`, {
    data: {
      email,
      password: currentPassword,
    },
  });
  expect(loginResponse.ok()).toBeTruthy();
  const loginPayload = await loginResponse.json();
  const userKey = readJwtPayload(loginPayload?.data?.accessToken).sub;
  expect(userKey).toBeTruthy();
  return issueDirectPasswordResetTokenForUserKey(userKey);
}

async function ensurePolicyBookmarkState(request, credentials, policyId, expectedBookmarked) {
  const loginResponse = await request.post(`${apiBaseUrl}/api/auth/login`, {
    data: {
      email: credentials.email,
      password: credentials.password,
    },
  });
  expect(loginResponse.ok()).toBeTruthy();
  const loginPayload = await loginResponse.json();
  const accessToken = loginPayload?.data?.accessToken;
  expect(accessToken).toBeTruthy();

  const bookmarksResponse = await request.get(`${apiBaseUrl}/api/users/me/bookmarks`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });
  expect(bookmarksResponse.ok()).toBeTruthy();
  const bookmarksPayload = await bookmarksResponse.json();
  const hasBookmark = (bookmarksPayload?.data ?? []).some((bookmark) => String(bookmark.id) === String(policyId));
  if (hasBookmark === expectedBookmarked) {
    return;
  }

  const bookmarkResponse = await request.post(`${apiBaseUrl}/api/policies/${policyId}/bookmark`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });
  expect(bookmarkResponse.ok()).toBeTruthy();
}

async function mockAlertsApis(page, {
  alerts = [],
  unreadCount = 0,
} = {}) {
  await page.route("**/api/notifications/me/unread-count", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify({
      success: true,
      data: { unreadCount },
    }),
  }));

  await page.route("**/api/notifications/me", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify({
      success: true,
      data: alerts,
    }),
  }));
}

async function primeAdminDashboardFailureMode(page, mode) {
  await page.addInitScript(([storageKey, failureMode]) => {
    window.localStorage.setItem(storageKey, failureMode);
  }, [adminDashboardE2EFailureStorageKey, mode]);
}

async function mockAdminDashboardApis(page) {
  const routes = [
    ["**/api/admin/dashboard/summary*", adminDashboardFixtures.summary],
    ["**/api/admin/dashboard/recommendation-breakdowns*", adminDashboardFixtures.breakdowns],
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

  await page.route("**/api/reference/official-codes/LOCAL_HOUSING_TYPE*", (route) => {
    const url = new URL(route.request().url());
    const queryText = (url.searchParams.get("q") || "").trim();
    const allRows = officialCodebookRows.LOCAL_HOUSING_TYPE;
    const rows = queryText
      ? allRows.filter((row) => Object.values(row).some((value) => String(value).includes(queryText)))
      : allRows;

    return route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify(buildOfficialCodebooksApiPayload({
        codeSetKey: "LOCAL_HOUSING_TYPE",
        sourceType: "xlsx",
        sourceFile: "주택유형구분코드 조회자료.xlsx",
        intendedUse: "user-profile and housing policy normalization",
        rowCount: 8,
        matchedRowCount: rows.length,
        sheetName: "주택유형구분코드 조회자료",
        headers: ["코드값", "코드값의미", "비고"],
        rows,
      })),
    });
  });

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
      sheetName: null,
      headers: null,
      rows: null,
      metadata: {
        activeRowCount: 135186,
        topLevelAgencyCount: 219,
        sampleRows: [
          {
            기관코드: "1741000",
            전체기관명: "행정안전부",
            유형분류_중_의미: "중앙행정기관",
          },
        ],
      },
    })),
  }));
}

async function loginToAdminDashboard(page) {
  await mockLoginApis(page, {
    roles: ["ROLE_ADMIN"],
    email: adminCredentials.email,
    name: "관리자",
  });
  await mockAlertsApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);
  await expect(page.getByText("운영 추천 대시보드")).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole("button", { name: "전체 새로고침" })).toBeVisible({ timeout: 60_000 });
}

test("비로그인 chat 접근 후 로그인하면 원래 chat 경로로 복귀한다", async ({ page }) => {
  await page.goto("/chat");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText("챗봇은 로그인 후 이용 가능합니다.")).toBeVisible();

  await loginThroughForm(page, userCredentials);
  await expectLoggedInChat(page);
});

test("로그인된 chat 세션이 만료되면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다", async ({ page }) => {
  await loginFromProtectedRoute(page, "/chat", userCredentials);
  await expectLoggedInChat(page);

  await page.waitForFunction(() => typeof window.__authExpired === "function");
  await page.evaluate(() => window.__authExpired());

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByPlaceholder("example@email.com")).toBeVisible();

  await loginThroughForm(page, userCredentials);
  await expectLoggedInChat(page);
});

test("chat 보호 API가 401 후 refresh도 실패하면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다", async ({ page }) => {
  let refreshAttemptCount = 0;
  const refreshAuthorizationHeaders = [];

  await page.route("**/api/chat/sessions", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 401,
        contentType: "application/json; charset=utf-8",
        body: JSON.stringify({ success: false, errorCode: "A001", message: "unauthorized" }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: [] }),
    });
  });

  await page.route("**/api/auth/refresh", async (route) => {
    refreshAttemptCount += 1;
    refreshAuthorizationHeaders.push(route.request().headers().authorization);
    await route.fulfill({
      status: 401,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: false, errorCode: "A003", message: "refresh expired" }),
    });
  });

  await mockLoginApis(page);
  await mockAlertsApis(page);
  await loginFromProtectedRoute(page, "/chat", userCredentials);
  await expectLoggedInChat(page);
  refreshAttemptCount = 0;
  refreshAuthorizationHeaders.length = 0;

  await page.getByRole("button", { name: "새 대화" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByPlaceholder("example@email.com")).toBeVisible();
  expect(refreshAttemptCount).toBe(1);
  expect(refreshAuthorizationHeaders).toEqual([undefined]);

  await page.unroute("**/api/auth/refresh");
  await loginThroughForm(page, userCredentials);
  await expectLoggedInChat(page);
});

test("로그인 401은 refresh를 시도하지 않고 원래 credential 오류를 보여준다", async ({ page }) => {
  let refreshAttemptCount = 0;

  await page.goto("/login");

  await page.route("**/api/auth/login", async (route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: false, errorCode: "A004", message: "이메일 또는 비밀번호가 올바르지 않습니다." }),
    });
  });

  await page.route("**/api/auth/refresh", async (route) => {
    refreshAttemptCount += 1;
    await route.fulfill({
      status: 401,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: false, errorCode: "A003", message: "이미 사용된 Refresh Token입니다. 재로그인이 필요합니다." }),
    });
  });

  await page.getByPlaceholder("example@email.com").fill("wrong@example.com");
  await page.getByPlaceholder("비밀번호를 입력하세요").fill("WrongPass123!");
  await page.getByRole("button", { name: "로그인" }).click();

  await expect(page.getByText("⚠ 이메일 또는 비밀번호가 올바르지 않습니다.")).toBeVisible();
  expect(refreshAttemptCount).toBe(0);
  await expect(page).toHaveURL(/\/login$/);
});

test("정책 목록 검색 query는 상세 진입 후 브라우저 back과 상세 뒤로가기에서 유지된다", async ({ page, request }) => {
  const firstPolicy = await openFirstSearchResult(page, request, "청년");
  await page.goBack();
  await expect(page).toHaveURL(/\/policies\?[^#]*search=%EC%B2%AD%EB%85%84/);
  await waitForProgressToSettle(page);
  await expectPolicyListItemReady(page, firstPolicy);

  await openPolicyFromList(page, firstPolicy);
  await Promise.all([
    page.waitForURL(/\/policies\?[^#]*search=%EC%B2%AD%EB%85%84/, { timeout: 15_000 }),
    page.getByRole("button", { name: /뒤로가기/ }).click(),
  ]);

  await expect(page).toHaveURL(/\/policies\?[^#]*search=%EC%B2%AD%EB%85%84/);
  await waitForProgressToSettle(page);
  await expectPolicyListItemReady(page, firstPolicy);
});

test("정책 상세는 요약 정보와 오류 제보 CTA를 보여준다", async ({ page, request }) => {
  const firstPolicy = await openFirstSearchResult(page, request, "청년");

  await expectPolicyDetailReady(page, firstPolicy);
  await expect(page.getByText("지원지역", { exact: true })).toBeVisible();
  await expect(page.getByText("소관기관", { exact: true })).toBeVisible();
  await expect(page.getByText("신청기간", { exact: true })).toBeVisible();
});

test("정책 상세에서 AI 신청 준비 코칭으로 진입하면 연결 정책과 신청 링크를 보여준다", async ({ page }) => {
  const policyId = 77701;
  const sessionId = 88001;
  const policy = {
    id: policyId,
    title: "청년 신청 코칭 지원",
    description: "신청 절차를 단계별로 확인해야 하는 정책입니다.",
    unifiedCategory: "주거",
    status: "ACTIVE",
    statusLabel: "진행중",
    sourceType: "YOUTH",
    hostOrg: "서울시",
    operatingOrg: "청년지원센터",
    providerName: "서울시",
    supportContent: "월세 부담을 낮추는 지원입니다.",
    applyMethodName: "온라인 신청",
    applyMethodDetail: "온라인 신청 후 제출서류를 업로드합니다.",
    targetDetail: "서울 거주 청년",
    supportDetail: "월세 일부 지원",
    selectionCriteria: "소득 및 거주 요건 확인",
    formFiles: "신청서, 주민등록등본",
    contactList: JSON.stringify([{ name: "청년지원센터", phone: "02-1234-5678" }]),
    detailUrl: "https://detail.example.com/policy",
    homepageUrl: "https://home.example.com",
    referenceUrlsJson: JSON.stringify([
      { url: "https://apply.example.com/policy", type: "APPLY", label: "신청 URL" },
      { url: "https://notice.example.com/policy", type: "REFERENCE", label: "공고문" },
    ]),
    applicationPeriod: "2026-06-01 ~ 2026-06-30",
    applyStartDate: "2026-06-01",
    applyEndDate: "2026-06-30",
    regionLabel: "서울특별시",
    regions: ["서울특별시"],
    tags: [{ tagType: "KEYWORD", tagValue: "주거" }],
    bookmarked: false,
    isOnlineApply: true,
    viewCount: 1,
  };
  const coachingReference = {
    serviceId: policyId,
    title: policy.title,
    reason: "신청 대상, 기간, 제출서류를 단계별로 확인하세요.",
    evidence: "신청방법: 온라인 신청 후 제출서류를 업로드합니다.",
    actionLinks: [
      {
        type: "OFFICIAL_APPLY",
        label: "공식 신청",
        url: "https://apply.example.com/policy",
        description: "신청은 공식 기관 페이지에서 진행하세요.",
      },
      {
        type: "DOCUMENTS",
        label: "공고/서류 확인",
        url: "https://notice.example.com/policy",
        description: "제출서류, 서식, 공고문을 확인할 때 사용하세요.",
      },
    ],
  };
  const messages = [
    {
      messageId: 1,
      role: "USER",
      content: "이 정책 신청 준비를 단계별로 도와줘.",
      referencedServiceIds: [],
      references: [],
      createdAt: "2026-06-19T09:00:00",
    },
    {
      messageId: 2,
      role: "ASSISTANT",
      content: "1. 자격 조건 확인\n2. 신청기간 확인\n3. 제출서류 확인\n공식 기관에서 최종 확인하세요.",
      referencedServiceIds: [policyId],
      references: [coachingReference],
      answerMode: "APPLICATION_COACHING",
      needsClarification: false,
      branchSuggestions: [],
      createdAt: "2026-06-19T09:00:01",
    },
  ];
  let sessionCreated = false;

  await mockLoginApis(page);
  await page.route("**/api/notifications/me/unread-count", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: { unreadCount: 0 } }),
    });
  });
  await page.route("**/api/notifications/me", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: [] }),
    });
  });
  await page.route("**/api/auth/refresh", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          accessToken: buildTestAccessToken(["ROLE_USER"]),
          refreshToken: "mock-refresh-token",
        },
      }),
    });
  });
  await page.route(`**/api/policies/${policyId}*`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: policy }),
    });
  });
  await page.route("**/api/policies?*", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: { content: [], totalElements: 0 } }),
    });
  });
  await page.route("**/api/chat/sessions", async (route) => {
    if (route.request().method() === "POST") {
      sessionCreated = true;
      await route.fulfill({
        status: 200,
        contentType: "application/json; charset=utf-8",
        body: JSON.stringify({
          success: true,
          data: { sessionId, title: "신청 준비", lastMessageAt: "2026-06-19T09:00:01", createdAt: "2026-06-19T09:00:00" },
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: sessionCreated
          ? [{ sessionId, title: "신청 준비", lastMessageAt: "2026-06-19T09:00:01", createdAt: "2026-06-19T09:00:00" }]
          : [],
      }),
    });
  });
  await page.route(`**/api/chat/sessions/${sessionId}/messages`, async (route) => {
    if (route.request().method() === "POST") {
      const body = route.request().postDataJSON();
      expect(body.coachPolicyId).toBe(policyId);
      await route.fulfill({
        status: 200,
        contentType: "application/json; charset=utf-8",
        body: JSON.stringify({
          success: true,
          data: {
            sessionId,
            answer: messages[1].content,
            needsClarification: false,
            answerMode: "APPLICATION_COACHING",
            branchSuggestions: [],
            references: [coachingReference],
          },
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: sessionCreated ? messages : [] }),
    });
  });

  await page.goto(`/policies/${policyId}`);
  await expectPolicyDetailReady(page, policy);

  await page.getByRole("button", { name: "AI와 신청 준비하기", exact: true }).click();
  await page.waitForURL(/\/(?:login|chat)(?:\?.*)?$/, { timeout: 15_000 });
  if (new URL(page.url()).pathname === "/login") {
    await loginThroughForm(page, userCredentials);
  }
  await expect(page).toHaveURL(/\/chat(?:\?.*)?$/);
  await expect(page.getByText("1. 자격 조건 확인", { exact: false })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(policy.title, { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "공식 신청", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "공고/서류 확인", exact: true })).toBeVisible();
});

test("이용가이드와 서비스 문의는 공개 진입면에서 서로 연결된다", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "이용가이드", exact: true }).first().click();
  await expect(page).toHaveURL(/\/guide$/);
  await expect(page.getByText("청년복지플랫폼을", { exact: false })).toBeVisible();
  await expect(page.getByText("서비스 문의", { exact: true }).first()).toBeVisible();

  await page.getByRole("button", { name: "문의 남기기", exact: true }).click();
  await expect(page).toHaveURL(/\/support$/);
  await expect(page.getByText("서비스 사용 문의 / 의견 제안", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "이용가이드 보기", exact: true }).click();
  await expect(page).toHaveURL(/\/guide$/);
});

test("서비스 문의 페이지는 정책 오류 제보와 역할을 구분해 안내한다", async ({ page }) => {
  await page.goto("/support");

  await expect(page.getByText("서비스 사용 문의 / 의견 제안", { exact: true })).toBeVisible();
  await expect(page.getByText("정책 오류 제보와는 다릅니다", { exact: true })).toBeVisible();
  await expect(page.getByText("정책 상세 페이지의 `정책 오류 제보`", { exact: false })).toBeVisible();
  await expect(page.getByRole("button", { name: "문의 보내기", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "이용가이드 보기", exact: true })).toBeVisible();
});

test("서비스 문의 페이지는 공개 문의를 접수하고 성공 안내를 보여준다", async ({ page }) => {
  await page.route("**/api/support/inquiries", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: { inquiryId: 1 } }),
    });
  });

  await page.goto("/support");
  await page.getByLabel("답변 받을 이메일").fill("tester@example.com");
  await page.getByLabel("문의 내용").fill("추천 결과가 기대와 다르게 보여서 사용 문의를 남깁니다.");
  await page.getByRole("button", { name: "문의 보내기", exact: true }).click();

  await expect(page.getByText("문의가 접수되었어요", { exact: true })).toBeVisible();
});

test("비로그인 정책 상세 오류 제보는 로그인으로 분기한다", async ({ page, request }) => {
  const firstPolicy = await openFirstSearchResult(page, request, "청년");
  await expectPolicyDetailReady(page, firstPolicy);

  await page.getByRole("button", { name: "정책 오류 제보", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByPlaceholder("example@email.com")).toBeVisible();
});

test("공개 사용자 핵심 흐름은 홈에서 가이드를 보고 정책 상세까지 이어진다", async ({ page, request }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "이용가이드", exact: true }).first().click();
  await expect(page).toHaveURL(/\/guide$/);
  await expect(page.getByText("청년복지플랫폼을", { exact: false })).toBeVisible();

  await page.getByRole("button", { name: "정책 검색하기", exact: true }).first().click();
  await expect(page).toHaveURL(/\/policies$/);

  const firstPolicy = await openFirstSearchResult(page, request, "청년");
  await expectPolicyDetailReady(page, firstPolicy);
});

test("정책 필터는 데스크톱에서 선택 즉시 반영되고 별도 적용 버튼을 요구하지 않는다", async ({ page }) => {
  await page.goto("/policies");

  const filterAside = page.locator("aside").first();
  await expect(filterAside.getByText("필터는 선택 즉시 반영됩니다", { exact: true })).toBeVisible();
  await filterAside.getByText("주거", { exact: true }).first().click();

  await expect(page).toHaveURL(/\/policies\?[^#]*category=%EC%A3%BC%EA%B1%B0/);
});

test("메인 재추천 CTA는 우선순위가 없으면 마이페이지 우선순위 탭으로 이동한다", async ({ page }) => {
  await page.route("**/api/users/me", async (route) => {
    if (route.request().method() !== "GET") {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          name: "테스터",
          priorities: [],
          houseTenureCode: "OWN",
          housingTypeCode: "APT",
          basicLivingRecipientTypeCode: "NONE",
          disabilityGradeCode: "NONE",
        },
      }),
    });
  });

  await page.goto("/login");
  await loginThroughForm(page, userCredentials);
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByText("추천 품질 우선 개선", { exact: true })).toBeVisible();
  await clickAndWaitForUrl(
    page.getByRole("button", { name: "맞춤 재추천 →", exact: true }),
    page,
    /\/mypage\?tab=1$/
  );
});

test("메인 개인 맞춤 재추천 CTA는 선택 프로필 공백이 크면 확인 CTA를 거쳐 마이페이지 내 정보 탭으로 이동한다", async ({ page }) => {
  await page.route("**/api/users/me", async (route) => {
    if (route.request().method() !== "GET") {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          name: "테스터",
          priorities: ["HOUSING"],
          houseTenureCode: null,
          housingTypeCode: null,
          basicLivingRecipientTypeCode: null,
          disabilityGradeCode: "NONE",
        },
      }),
    });
  });

  await page.goto("/login");
  await loginThroughForm(page, userCredentials);
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("button", { name: "마이페이지에서 채우기 →", exact: true })).toBeVisible();
  await expect(page.getByText("추천 정확도 보강", { exact: true }).first()).toBeVisible();
  await page.getByRole("button", { name: "맞춤 재추천 →", exact: true }).click();
  await expect(page.getByText("추천 전 확인", { exact: true })).toBeVisible();
  await expect(page.getByText("선택 프로필 1/4개 입력 상태입니다", { exact: true })).toBeVisible();
  await clickAndWaitForUrl(
    page.getByRole("button", { name: "선택 정보 보완하기 →", exact: true }),
    page,
    /\/mypage\?tab=0$/
  );
});

test("마이페이지 내 정보 저장 후 선택 프로필 리마인드는 보완 입력으로 이어진다", async ({ page }) => {
  await page.route("**/api/users/me", async (route) => {
    const method = route.request().method();
    if (method === "PUT") {
      await route.fulfill({
        status: 200,
        contentType: "application/json; charset=utf-8",
        body: JSON.stringify({ success: true, data: null }),
      });
      return;
    }
    if (method !== "GET") {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          name: "테스터",
          email: userCredentials.email,
          birthDate: "2000-01-01",
          sido: "서울",
          sgg: "서울 종로구",
          incomeLevel: 5,
          employmentStatus: "학생",
          householdType: "1인가구",
          priorities: [{ code: "HOUSING", name: "주거" }],
          targetTypes: [],
          houseTenureCode: null,
          housingTypeCode: null,
          basicLivingRecipientTypeCode: null,
          disabilityGradeCode: "NONE",
        },
      }),
    });
  });

  await loginFromProtectedRoute(page, "/mypage?tab=0", userCredentials);
  await page.getByRole("button", { name: "수정", exact: true }).click();
  await page.getByRole("button", { name: "저장", exact: true }).click();
  await expect(page.getByText("저장 완료", { exact: true })).toBeVisible();
  await expect(page.getByText("선택 프로필 1/4개 입력됨", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "선택 정보 보완하기 →", exact: true }).first().click();
  await expect(page.locator("#profile-standard-code-section")).toBeVisible();
});

test("마이페이지 동의 철회는 선택정보와 민감정보 철회 API를 호출하고 체크 상태를 해제한다", async ({ page }) => {
  const withdrawnTypes = [];

  await mockLoginApis(page);
  await mockAlertsApis(page);
  await page.route("**/api/users/me", async (route) => {
    if (route.request().method() !== "GET") {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          name: "테스터",
          email: userCredentials.email,
          birthDate: "2000-01-01",
          sido: "서울",
          sgg: "관악구",
          incomeLevel: 5,
          employmentStatus: "학생",
          householdType: "1인가구",
          houseTenureCode: "3",
          housingTypeCode: "4",
          basicLivingRecipientTypeCode: "1",
          disabilityGradeCode: "041",
          optionalProfileConsentAgreed: true,
          sensitiveInfoConsentAgreed: true,
          notificationYn: false,
          notificationEmailYn: true,
          notificationInAppYn: true,
          notificationWebPushYn: false,
          notificationPeriod: "NONE",
          displayCount: 10,
          priorities: [{ rank: 1, code: "HOUSING", weight: 2.0 }],
          targetTypes: ["자립준비청년"],
        },
      }),
    });
  });
  await page.route("**/api/users/me/bookmarks", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: [] }),
    });
  });
  await page.route("**/api/users/me/recent-viewed-policies?*", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: [] }),
    });
  });
  await page.route("**/api/users/me/consents/*", async (route) => {
    expect(route.request().method()).toBe("DELETE");
    withdrawnTypes.push(route.request().url().split("/").at(-1));
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: null }),
    });
  });

  await loginFromProtectedRoute(page, "/mypage?tab=0", userCredentials);
  await expect(page).toHaveURL(/\/mypage\?tab=0$/);
  const optionalConsent = page.getByLabel(/추천용 선택 개인정보/);
  const sensitiveConsent = page.getByLabel(/민감정보 수집/);
  await expect(optionalConsent).toBeChecked();
  await expect(sensitiveConsent).toBeChecked();
  const standardCodeSelects = page.locator("#profile-standard-code-section select");

  await page.getByRole("button", { name: "선택정보 동의 철회", exact: true }).click();
  await expect(page.getByText("선택정보 동의를 철회할까요?", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "선택정보 철회", exact: true }).click();
  await expect(optionalConsent).not.toBeChecked();
  await expect(standardCodeSelects.nth(0)).toHaveValue("");
  await expect(standardCodeSelects.nth(1)).toHaveValue("");
  await expect(standardCodeSelects.nth(2)).toHaveValue("");

  await page.getByRole("button", { name: "민감정보 동의 철회", exact: true }).click();
  await expect(page.getByText("민감정보 동의를 철회할까요?", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "민감정보 철회", exact: true }).click();
  await expect(sensitiveConsent).not.toBeChecked();
  await expect(standardCodeSelects.nth(3)).toHaveValue("");

  expect(withdrawnTypes).toEqual(["OPTIONAL_PROFILE", "SENSITIVE_INFO"]);
});

test("로그인 직후 메인에서는 우선순위와 선택 프로필 공백에 대한 추천 nudge를 한 번 보여준다", async ({ page }) => {
  await page.route("**/api/users/me", async (route) => {
    if (route.request().method() !== "GET") {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          name: "테스터",
          email: userCredentials.email,
          priorities: [],
          houseTenureCode: null,
          housingTypeCode: null,
          basicLivingRecipientTypeCode: null,
          disabilityGradeCode: "NONE",
        },
      }),
    });
  });

  await page.goto("/login");
  await loginThroughForm(page, userCredentials);

  await expect(page.getByText("로그인되었습니다. 우선순위와 선택 프로필 1/4 입력 상태를 함께 보완하면 추천 품질이 더 빨리 안정됩니다.")).toBeVisible();
});

test("로그인 사용자 핵심 흐름은 메인에서 가이드와 추천 보강으로 이어진다", async ({ page }) => {
  await page.route("**/api/users/me", async (route) => {
    if (route.request().method() !== "GET") {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          name: "테스터",
          email: userCredentials.email,
          priorities: [],
          houseTenureCode: null,
          housingTypeCode: null,
          basicLivingRecipientTypeCode: null,
          disabilityGradeCode: null,
        },
      }),
    });
  });

  await page.goto("/login");
  await loginThroughForm(page, userCredentials);

  await expect(page.getByText("처음 시작 가이드", { exact: true })).toBeVisible();
  await expect(page.getByText("추천 품질 우선 개선", { exact: true })).toBeVisible();

  await clickAndWaitForUrl(
    page.getByRole("button", { name: "이용가이드 보기 →", exact: true }),
    page,
    /\/guide$/
  );

  await Promise.all([
    page.waitForURL(/\/$/, { timeout: 15_000 }),
    page.goBack(),
  ]);
  await expect(page.getByText("추천 품질 우선 개선", { exact: true })).toBeVisible();
  await clickAndWaitForUrl(
    page.getByRole("button", { name: "맞춤 재추천 →", exact: true }),
    page,
    /\/mypage\?tab=1$/
  );
});

test("마이페이지 북마크 탭에서 상세로 갔다가 뒤로오면 tab query가 유지된다", async ({ page, request }) => {
  const policy = await fetchFirstSearchResult(request, "청년");
  await ensurePolicyBookmarked(request, userCredentials, policy.id);

  await loginFromProtectedRoute(page, "/mypage?tab=2", userCredentials);
  let bookmark = await expectBookmarkTabReady(page, policy);

  await Promise.all([
    page.waitForURL(policyDetailUrl(policy.id), { timeout: 15_000 }),
    bookmark.click(),
  ]);
  await expectPolicyDetailReady(page, policy);
  await Promise.all([
    page.waitForURL(/\/mypage\?tab=2$/, { timeout: 15_000 }),
    page.getByRole("button", { name: /뒤로가기/ }).click(),
  ]);

  await expectBookmarkTabReady(page, policy);
});

test("보호 사용자 핵심 흐름은 로그인 요구 경로와 마이페이지 복귀를 유지한다", async ({ page, request }) => {
  const policy = await fetchFirstSearchResult(request, "청년");
  await ensurePolicyBookmarked(request, userCredentials, policy.id);

  await mockLoginApis(page);
  await mockAlertsApis(page);
  await page.route("**/api/chat/sessions", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: [] }),
    });
  });
  await page.route("**/api/users/me/bookmarks", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: [policy] }),
    });
  });
  await page.route("**/api/users/me/recent-viewed-policies?*", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: [] }),
    });
  });

  await loginFromProtectedRoute(page, "/chat", userCredentials);
  await mockAuthRefreshApi(page);
  await expectLoggedInChat(page);

  await page.goto("/mypage?tab=2");
  let bookmark = await expectBookmarkTabReady(page, policy);

  await Promise.all([
    page.waitForURL(policyDetailUrl(policy.id), { timeout: 15_000 }),
    bookmark.click(),
  ]);
  await expectPolicyDetailReady(page, policy);
  await Promise.all([
    page.waitForURL(/\/mypage\?tab=2$/, { timeout: 15_000 }),
    page.getByRole("button", { name: /뒤로가기/ }).click(),
  ]);

  await expectBookmarkTabReady(page, policy);
});

test("마이페이지 비밀번호 변경 후 로그인으로 이동하고 재로그인하면 account tab으로 복귀한다", async ({ page }) => {
  await page.route("**/api/users/me/password", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: null }),
    });
  });

  await loginFromProtectedRoute(page, "/mypage?tab=5", userCredentials);
  await expect(page).toHaveURL(/\/mypage\?tab=5$/);
  await expect(page.getByRole("button", { name: "비밀번호 변경" })).toBeVisible();

  const passwordInputs = page.locator('input[type="password"]');
  await passwordInputs.nth(0).fill(userCredentials.password);
  await page.getByPlaceholder("새 비밀번호 입력").fill("TempPass123!");
  await page.getByPlaceholder("다시 한 번 입력").fill("TempPass123!");
  await page.getByRole("button", { name: "비밀번호 변경" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByPlaceholder("example@email.com")).toBeVisible();

  await loginThroughForm(page, userCredentials);
  await expect(page).toHaveURL(/\/mypage\?tab=5$/);
  await expect(page.getByRole("button", { name: "비밀번호 변경" })).toBeVisible();
});

test("알림함 빈 상태 CTA는 정책 목록과 알림 설정으로 이어진다", async ({ page }) => {
  await mockLoginApis(page);
  await mockAlertsApis(page, { alerts: [], unreadCount: 0 });

  await loginFromProtectedRoute(page, "/alerts", userCredentials);
  await mockAuthRefreshApi(page);
  await expect(page).toHaveURL(/\/alerts$/);
  const emptyStateSection = page.getByText("도착한 알림이 아직 없어요", { exact: true }).locator("..").locator("..");
  await expect(page.getByText("도착한 알림이 아직 없어요", { exact: true })).toBeVisible();

  await emptyStateSection.getByRole("button", { name: "정책 보러가기", exact: true }).click();
  await expect(page).toHaveURL(/\/policies$/);

  await page.goto("/alerts");
  await expect(page.getByText("도착한 알림이 아직 없어요", { exact: true })).toBeVisible();
  const refreshedEmptyStateSection = page.getByText("도착한 알림이 아직 없어요", { exact: true }).locator("..").locator("..");
  await refreshedEmptyStateSection.getByRole("button", { name: "알림 설정 열기", exact: true }).click();
  await expect(page).toHaveURL(/\/mypage\?tab=3$/);
});

test("알림 설정 선택 프로필 보강 CTA는 내 정보 입력 섹션으로 이어진다", async ({ page }) => {
  await page.route("**/api/users/me", async (route) => {
    if (route.request().method() !== "GET") {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({
        success: true,
        data: {
          name: "테스터",
          email: userCredentials.email,
          birthDate: "2000-01-01",
          sido: "서울",
          sgg: "서울 종로구",
          incomeLevel: 5,
          employmentStatus: "학생",
          householdType: "1인가구",
          priorities: [{ code: "HOUSING", name: "주거" }],
          targetTypes: [],
          notificationYn: true,
          notificationEmailYn: true,
          notificationInAppYn: true,
          notificationWebPushYn: false,
          houseTenureCode: null,
          housingTypeCode: null,
          basicLivingRecipientTypeCode: null,
          disabilityGradeCode: "NONE",
        },
      }),
    });
  });

  await loginFromProtectedRoute(page, "/mypage?tab=3", userCredentials);
  await expect(page.getByText("알림 추천 기준 보강", { exact: true })).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/mypage\?tab=0$/, { timeout: 15_000 }),
    page.getByRole("button", { name: "알림 기준 보강하기 →", exact: true }).click(),
  ]);
  await expect(page.getByText("주거 및 생활 여건", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "저장", exact: true })).toBeEnabled();
});

test("알림함에서 unread 알림을 열면 읽음 처리 후 deeplink로 이동한다", async ({ page }) => {
  let readPatchCount = 0;

  await mockLoginApis(page);
  await mockAlertsApis(page, {
    unreadCount: 1,
    alerts: [{
      id: 9001,
      kind: "RECOMMENDATION_DIGEST",
      status: "UNREAD",
      title: "청년 월세 지원 추천이 도착했어요",
      body: "월세 지원 정책 3건을 확인해보세요.",
      deeplinkUrl: "/policies/7751",
      createdAt: "2026-06-03T10:00:00Z",
    }],
  });

  await page.route("**/api/notifications/9001/read", async (route) => {
    readPatchCount += 1;
    await route.fulfill({
      status: 200,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: true, data: null }),
    });
  });

  await loginFromProtectedRoute(page, "/alerts", userCredentials);
  await expect(page).toHaveURL(/\/alerts$/);
  await expect(page.getByText("청년 월세 지원 추천이 도착했어요", { exact: true })).toBeVisible();
  const readPatchPromise = page.waitForResponse((response) =>
    response.request().method() === "PATCH" &&
    response.url().includes("/api/notifications/9001/read") &&
    response.ok()
  );

  await Promise.all([
    readPatchPromise,
    page.getByRole("button", { name: "열기", exact: true }).click(),
  ]);

  expect(readPatchCount).toBe(1);
  await expect(page).toHaveURL(/\/policies\/7751$/);
});

test("알림함 deeplink는 내부 경로만 이동 대상으로 허용한다", async ({ page }) => {
  await mockLoginApis(page);
  await mockAlertsApis(page, {
    unreadCount: 0,
    alerts: [{
      id: 9002,
      kind: "RECOMMENDATION_DIGEST",
      status: "READ",
      title: "외부 deeplink 알림",
      body: "외부 URL은 이동하지 않아야 합니다.",
      deeplinkUrl: "https://evil.example/policies/7751",
      createdAt: "2026-06-03T10:00:00Z",
    }],
  });

  await loginFromProtectedRoute(page, "/alerts", userCredentials);
  await expect(page).toHaveURL(/\/alerts$/);
  await page.getByRole("button", { name: "열기", exact: true }).click();

  await expect(page).toHaveURL(/\/alerts$/);
  await expect(page.getByText("연결된 화면이 없는 알림입니다")).toBeVisible();
});

test("개인 유지 흐름은 알림함과 북마크 탭을 이어서 보여준다", async ({ page, request }) => {
  const policy = await fetchFirstSearchResult(request, "청년");
  await ensurePolicyBookmarked(request, userCredentials, policy.id);
  await mockAlertsApis(page, { alerts: [], unreadCount: 0 });

  await loginFromProtectedRoute(page, "/alerts", userCredentials);
  await expect(page).toHaveURL(/\/alerts$/);
  await expect(page.getByText("도착한 알림이 아직 없어요", { exact: true })).toBeVisible();

  const emptyStateSection = page.getByText("도착한 알림이 아직 없어요", { exact: true }).locator("..").locator("..");
  await emptyStateSection.getByRole("button", { name: "알림 설정 열기", exact: true }).click();
  await expect(page).toHaveURL(/\/mypage\?tab=3$/);

  await Promise.all([
    page.waitForURL(/\/mypage\?tab=2$/, { timeout: 15_000 }),
    page.getByRole("button", { name: /북마크/ }).first().click(),
  ]);
  await expectBookmarkTabReady(page, policy);
});

test("비로그인 정책 상세 북마크는 로그인 후 bookmark POST가 정확히 1회만 실행된다", async ({ page, request }) => {
  const policy = await fetchFirstSearchResult(request, "청년");
  let bookmarkPostCount = 0;

  await ensurePolicyBookmarkState(request, userCredentials, policy.id, false);

  await page.route(`**/api/policies/${policy.id}/bookmark`, async (route) => {
    bookmarkPostCount += 1;
    const response = await route.fetch();
    await route.fulfill({ response });
  });

  await page.goto(`/policies/${policy.id}`);
  await expectPolicyDetailReady(page, policy);
  await expect(page.getByRole("button", { name: "♡ 북마크에 저장" })).toBeVisible();
  await page.getByRole("button", { name: "♡ 북마크에 저장" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await loginThroughForm(page, userCredentials);

  await expectPolicyDetailReady(page, policy);
  await expect(page.getByRole("button", { name: "★ 북마크됨" })).toBeVisible();
  expect(bookmarkPostCount).toBe(1);
});

test("reset-password query token 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다", async ({ page, request }) => {
  const nonce = `${Date.now()}`;
  const email = `playwright.reset.${nonce}@example.com`;
  const originalPassword = "Password123!";
  const newPassword = "ResetPass123!";

  await signupUser(request, {
    email,
    password: originalPassword,
  });

  const resetToken = await issuePasswordReset(request, email, originalPassword);

  await page.goto(`/reset-password?token=${encodeURIComponent(resetToken)}`);
  await expect(page).toHaveURL(new RegExp(`/reset-password#token=${encodeURIComponent(resetToken)}`));
  await expect(page.getByText("새 비밀번호 설정", { exact: true })).toBeVisible();

  await page.getByPlaceholder("8자 이상 입력").fill(newPassword);
  await page.getByPlaceholder("비밀번호를 다시 입력").fill(newPassword);
  await page.getByRole("button", { name: "비밀번호 변경" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await loginThroughForm(page, { email, password: newPassword });
  await expect(page).toHaveURL("/");
});

test("reset-password hash token 딥링크 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다", async ({ page, request }) => {
  const nonce = `${Date.now()}`;
  const email = `playwright.reset.hash.${nonce}@example.com`;
  const originalPassword = "Password123!";
  const newPassword = "ResetHash123!";

  await signupUser(request, {
    email,
    password: originalPassword,
    name: "해시리셋테스트",
  });

  const resetToken = await issuePasswordReset(request, email, originalPassword);

  await page.goto(`/reset-password#token=${encodeURIComponent(resetToken)}`);
  await expect(page).toHaveURL(new RegExp(`/reset-password#token=${encodeURIComponent(resetToken)}`));
  await expect(page.getByText("새 비밀번호 설정", { exact: true })).toBeVisible();

  await page.getByPlaceholder("8자 이상 입력").fill(newPassword);
  await page.getByPlaceholder("비밀번호를 다시 입력").fill(newPassword);
  await page.getByRole("button", { name: "비밀번호 변경" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await loginThroughForm(page, { email, password: newPassword });
  await expect(page).toHaveURL("/");
});

test("reset-password invalid token 진입은 만료 안내를 보여주고 로그인으로 이동하지 않는다", async ({ page }) => {
  await page.goto("/reset-password#token=definitely-invalid-reset-token");
  await expect(page.getByText("새 비밀번호 설정", { exact: true })).toBeVisible();

  await page.getByPlaceholder("8자 이상 입력").fill("Invalid123!");
  await page.getByPlaceholder("비밀번호를 다시 입력").fill("Invalid123!");
  await page.getByRole("button", { name: "비밀번호 변경" }).click();

  await expect(page.getByText("링크가 만료되었거나 이미 사용되었습니다. 다시 재설정 메일을 요청해주세요.")).toBeVisible();
  await expect(page).toHaveURL(/\/reset-password#token=definitely-invalid-reset-token$/);
  await expect(page.getByPlaceholder("8자 이상 입력")).toBeVisible();
});

test("일반 사용자로 admin dashboard 접근 시 홈으로 리다이렉트되고 경고 toast가 보인다", async ({ page }) => {
  await loginFromProtectedRoute(page, "/admin/dashboard", userCredentials);
  await expect(page).toHaveURL("/");
  await expect(page.getByText("운영 대시보드는 관리자 계정만 접근할 수 있습니다.")).toBeVisible();
});

test("admin dashboard는 recommendation overview와 triage 섹션을 함께 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  await expect(page.getByText("운영 추천 대시보드")).toBeVisible();
  await expect(page.locator(section("admin-recommendation-overview")).getByText("추천 검토 상태", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("수집 실패 상세 로딩 중")).toHaveCount(0);
  await expect(page.getByText("수집 실패 상세", { exact: true })).toBeVisible();
  await expect(page.getByText("최근 0건 검색 키워드", { exact: true })).toBeVisible();
});

test("admin dashboard quick jump는 recommendation breakdown 섹션으로 이동한다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);
  await expect(page.getByText("운영 추천 대시보드")).toBeVisible();
  await page.getByRole("button", { name: /추천 상세/ }).click();
  await expect(page.getByText("반복 노출 상위 서비스")).toBeVisible();
  await expect(page.getByText("1순위 분포 선두 서비스")).toBeVisible();
});

test("admin dashboard summary 실패 시 collect/search triage는 유지된다 @dev-only", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await primeAdminDashboardFailureMode(page, "summary");
  await loginToAdminDashboard(page);

  await expect(page.getByText("운영 요약 로딩 중")).toHaveCount(0);
  await expect(page.getByText("운영 요약 로드 실패")).toBeVisible();
  await expect(page.getByText("summary forced failure")).toBeVisible();
  await expect(page.getByText("일부 섹션만 불러오지 못했습니다.")).toBeVisible();
  await expect(page.getByText("수집 실패 상세")).toBeVisible();
  await expect(page.getByText("검색 실패 상세")).toBeVisible();
});

test("admin dashboard breakdown 실패 시 recommendation hero는 유지되고 해당 섹션만 실패한다 @dev-only", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await primeAdminDashboardFailureMode(page, "breakdown");
  await loginToAdminDashboard(page);

  await expect(page.getByText("추천 상세 진단 로딩 중")).toHaveCount(0);
  await expect(page.getByText("운영 추천 대시보드")).toBeVisible();
  await expect(page.locator(section("admin-recommendation-overview")).getByText("추천 검토 상태", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("일부 섹션만 불러오지 못했습니다.")).toBeVisible();
  await expect(page.getByText("추천 상세 진단 로드 실패")).toBeVisible();
  await expect(page.getByText("breakdown forced failure")).toBeVisible();
});

test("admin dashboard 공식 코드북 탐색은 검색과 메타데이터를 보여준다 @admin-required", async ({ page }) => {
  test.setTimeout(150_000);
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  await expect(page.getByText("공식 코드북 탐색", { exact: true })).toBeVisible();
  await expect(page.getByText("행 조회 가능", { exact: true })).toBeVisible();
  await expect(page.getByText("아파트", { exact: true })).toBeVisible();
  await expect(page.getByText("다가구주택", { exact: true })).toBeVisible();

  await page.getByPlaceholder("코드값, 라벨, 설명 검색").fill("아파트");
  await expect(page.getByText("다가구주택", { exact: true })).toHaveCount(0);
  await expect(page.getByText("아파트", { exact: true })).toBeVisible();

  const referenceCodebooksSection = page.locator(section("admin-reference-codebooks"));
  await referenceCodebooksSection.getByRole("combobox").click();
  await page.getByRole("option", { name: "LOCAL_AGENCY_CODES", exact: true }).click();

  await expect(page.getByText("메타데이터 전용", { exact: true })).toBeVisible();
  await expect(page.getByText(/activeRowCount: 135186/)).toBeVisible();
  await expect(page.getByText("행정안전부", { exact: true })).toBeVisible();
});

test("admin dashboard 표준코드 입력률 섹션은 coverage를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const standardCodeCoverageSection = page.locator(section("admin-standard-code-coverage"));

  await expect(standardCodeCoverageSection.getByText("주거·복지 표준코드 입력률", { exact: true })).toBeVisible();
  await expect(standardCodeCoverageSection.getByText("전체 사용자", { exact: true })).toBeVisible();
  await expect(standardCodeCoverageSection.getByText("전부 미입력", { exact: true })).toBeVisible();
  await expect(standardCodeCoverageSection.getByText("주택유형 입력", { exact: true })).toBeVisible();
  await expect(standardCodeCoverageSection.getByText("안전 보정 후보", { exact: true })).toBeVisible();
  await expect(standardCodeCoverageSection.getByText("796", { exact: true })).toBeVisible();
  await expect(standardCodeCoverageSection.getByText("789", { exact: true })).toBeVisible();
  await expect(standardCodeCoverageSection.getByText("3", { exact: true }).first()).toBeVisible();
});

test("admin dashboard 정책 오류 제보 섹션은 열린 제보 recent queue를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const policyErrorReportsSection = page.locator(section("admin-policy-error-reports"));

  await expect(policyErrorReportsSection.getByText("정책 오류 제보 recent queue", { exact: true })).toBeVisible();
  await expect(policyErrorReportsSection.getByText("열린 제보", { exact: true })).toBeVisible();
  await expect(policyErrorReportsSection.getByText("최근 24시간 신규", { exact: true })).toBeVisible();
  await expect(policyErrorReportsSection.getByText("표시 제보", { exact: true })).toBeVisible();
  await expect(policyErrorReportsSection.getByText("열린 건", { exact: true })).toBeVisible();
  await expect(policyErrorReportsSection.getByText("처리완료", { exact: true }).first()).toBeVisible();
  await expect(policyErrorReportsSection.getByText("전체", { exact: true }).first()).toBeVisible();
  await expect(policyErrorReportsSection.getByText("2", { exact: true }).first()).toBeVisible();
  await expect(policyErrorReportsSection.getByText("청년 월세 한시 특별지원", { exact: true })).toBeVisible();
  await expect(policyErrorReportsSection.getByText("지역 정보가 다릅니다", { exact: true })).toBeVisible();
  await expect(policyErrorReportsSection.getByText("청년 자격시험 응시료 지원사업", { exact: true })).toBeVisible();
  await expect(policyErrorReportsSection.getByText("링크나 원문이 열리지 않습니다", { exact: true })).toBeVisible();
});

test("admin dashboard 서비스 문의 섹션은 열린 문의 recent queue를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const supportInquiriesSection = page.locator(section("admin-support-inquiries"));

  await expect(supportInquiriesSection.getByText("서비스 문의 recent queue", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("열린 문의", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("최근 24시간 신규", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("표시 문의", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("열린 건", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("처리완료", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("전체", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("1", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("사용법 질문", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("챗봇이 이전 질문 맥락을 잘 못 이어갑니다.", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("오류/버그", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("필터가 바로 적용되는지 헷갈립니다.", { exact: true })).toBeVisible();
});

test("admin dashboard 정책 중복 review 섹션은 duplicate queue를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const duplicateSection = page.locator(section("admin-policy-duplicate-groups"));

  await expect(duplicateSection.getByText("정책 중복 review queue", { exact: true })).toBeVisible();
  await expect(duplicateSection.getByText("열린 중복 묶음", { exact: true })).toBeVisible();
  await expect(duplicateSection.getByText("최근 24시간 신규", { exact: true })).toBeVisible();
  await expect(duplicateSection.getByText("열린 관련 row", { exact: true })).toBeVisible();
  await expect(duplicateSection.getByText("표시 묶음", { exact: true })).toBeVisible();
  await expect(duplicateSection.getByText("열린 건", { exact: true })).toBeVisible();
  await expect(duplicateSection.getByText("처리완료", { exact: true }).first()).toBeVisible();
  await expect(duplicateSection.getByText("전체", { exact: true }).first()).toBeVisible();
  await expect(duplicateSection.getByText("청년문화예술패스", { exact: true })).toBeVisible();
  await expect(duplicateSection.getByText("공공근로사업", { exact: true })).toBeVisible();
  await expect(duplicateSection.getByText("12건 중복", { exact: true })).toBeVisible();
  await expect(duplicateSection.getByText("처리완료 · admin-user-key")).toBeVisible();
});

test("admin dashboard 정책 링크 review 섹션은 열린 링크 review queue를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const linkReviewSection = page.locator(section("admin-policy-link-reviews"));

  await expect(linkReviewSection.getByText("정책 링크 review queue", { exact: true })).toBeVisible();
  await expect(linkReviewSection.getByText("열린 링크 검토", { exact: true })).toBeVisible();
  await expect(linkReviewSection.getByText("최근 24시간 신규", { exact: true })).toBeVisible();
  await expect(linkReviewSection.getByText("표시 항목", { exact: true })).toBeVisible();
  await expect(linkReviewSection.getByText("열린 건", { exact: true })).toBeVisible();
  await expect(linkReviewSection.getByText("처리완료", { exact: true }).first()).toBeVisible();
  await expect(linkReviewSection.getByText("전체", { exact: true }).first()).toBeVisible();
  await expect(linkReviewSection.getByText("청년 창업 실험실 지원사업", { exact: true })).toBeVisible();
  await expect(linkReviewSection.getByText("지역 청년 문화기획단 모집", { exact: true })).toBeVisible();
  await expect(linkReviewSection.getByText("대표 링크 비어있음", { exact: true }).first()).toBeVisible();
  await expect(linkReviewSection.getByText("프로그램형", { exact: true })).toBeVisible();
  await expect(linkReviewSection.getByText("공고/모집형", { exact: true })).toBeVisible();
  await expect(linkReviewSection.getByText("처리완료 · admin-user-key")).toBeVisible();
});

test("admin dashboard policy triage 요약은 duplicate/link 우선순위를 상단 카드에 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const triageSummarySection = page.locator(section("admin-policy-triage-summary"));

  await expect(triageSummarySection.getByText("policy triage", { exact: true })).toBeVisible();
  await expect(triageSummarySection.getByText("중복 우선", { exact: true })).toBeVisible();
  await expect(triageSummarySection.getByText("exact duplicate", { exact: true })).toBeVisible();
  await expect(triageSummarySection.getByText("12", { exact: true }).first()).toBeVisible();
  await expect(triageSummarySection.getByText("mirror 16 · 열린 중복 139", { exact: true })).toBeVisible();
  await expect(triageSummarySection.getByText("급부형 링크 review", { exact: true })).toBeVisible();
  await expect(triageSummarySection.getByText("33", { exact: true }).first()).toBeVisible();
  await expect(triageSummarySection.getByText("모집형 12 · 열린 링크 164", { exact: true })).toBeVisible();
  await expect(triageSummarySection.getByText("현재 처리 순서", { exact: true })).toBeVisible();
  await expect(triageSummarySection.getByText("exact -> mirror -> link", { exact: true })).toBeVisible();
});

test("admin dashboard stale notification target 섹션은 오래된 unread target cluster를 보여준다 @admin-required", async ({ page }) => {
  test.setTimeout(150_000);
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const staleTargetSection = page.locator(section("admin-notification-stale-targets"));

  await expect(staleTargetSection.getByText("stale notification target queue", { exact: true })).toBeVisible();
  await expect(staleTargetSection.getByText("stale unread", { exact: true })).toBeVisible();
  await expect(staleTargetSection.getByText("stale target 묶음", { exact: true })).toBeVisible();
  await expect(staleTargetSection.getByText("표시 target", { exact: true })).toBeVisible();
  await expect(staleTargetSection.getByText("북마크한 정책 마감이 임박했어요", { exact: true })).toBeVisible();
  await expect(staleTargetSection.getByText("맞춤 정책 추천이 도착했어요", { exact: true })).toBeVisible();
  await expect(staleTargetSection.getByText(/\/policies\/2622/)).toBeVisible();
  await expect(staleTargetSection.getByText("5건 / 5명", { exact: true })).toBeVisible();
  await expect(staleTargetSection.getByRole("button", { name: "14일 초과 숨기기" }).first()).toBeVisible();

  await staleTargetSection.getByText("7일 초과", { exact: true }).click();
  await expect(page.getByRole("button", { name: "전체 새로고침" })).toBeVisible({ timeout: 60_000 });
  await expect(staleTargetSection.getByText("11", { exact: true }).first()).toBeVisible();
  await expect(staleTargetSection.getByText("4", { exact: true }).first()).toBeVisible();
  await expect(staleTargetSection.getByText("북마크한 정책 신청 마감 7일 전이에요", { exact: true })).toBeVisible();
  await expect(staleTargetSection.getByRole("button", { name: "7일 초과 숨기기" }).first()).toBeVisible();
});

test("admin dashboard 표준코드 추천 효과 섹션은 matrix 결과를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const standardCodeEffectSection = page.locator(section("admin-standard-code-effect"));

  await expect(standardCodeEffectSection.getByText("표준코드 추천 효과", { exact: true })).toBeVisible();
  await expect(standardCodeEffectSection.getByText("주거 효과 rule 상승", { exact: true })).toBeVisible();
  await expect(standardCodeEffectSection.getByText("2", { exact: true }).first()).toBeVisible();
  await expect(standardCodeEffectSection.getByText("복지 matrix 시나리오", { exact: true })).toBeVisible();
  await expect(standardCodeEffectSection.getByText("4", { exact: true }).first()).toBeVisible();
  await expect(standardCodeEffectSection.getByText("최대 rule delta", { exact: true })).toBeVisible();
  await expect(standardCodeEffectSection.getByText("54", { exact: true }).first()).toBeVisible();
  await expect(standardCodeEffectSection.getByText("basic_living_and_housing_combo").first()).toBeVisible();
});

test("admin dashboard 상위 wrapper 섹션은 active baseline/current priority 요약을 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const wrapperObservationSection = page.locator(section("admin-wrapper-observation"));

  await expect(wrapperObservationSection.getByText("active baseline / current priority", { exact: true })).toBeVisible();
  await expect(wrapperObservationSection.getByText("active baseline", { exact: true })).toBeVisible();
  await expect(wrapperObservationSection.getByText("current priority", { exact: true })).toBeVisible();
  await expect(wrapperObservationSection.getByText("/tmp/active-baseline-suite/latest-active-baseline-summary.txt")).toBeVisible();
  await expect(wrapperObservationSection.getByText("/tmp/current-priority-suite/latest-current-priority-summary.txt")).toBeVisible();
  await expect(wrapperObservationSection.getByText("active baseline reused", { exact: true })).toBeVisible();
  await expect(wrapperObservationSection.getByText("attention feed", { exact: true })).toHaveCount(2);
  await expect(wrapperObservationSection.getByText(/attention 표준코드 입력 backlog/)).toHaveCount(2);
  await expect(wrapperObservationSection.getByText("true", { exact: true }).first()).toBeVisible();
  await expect(wrapperObservationSection.getByText("793", { exact: true }).first()).toBeVisible();
  await expect(wrapperObservationSection.getByText(/54(\.0)?/).first()).toBeVisible();
});

test("admin dashboard 운영 스냅샷은 wrapper 핵심 수치를 상단 카드에 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  await expect(page.getByText("표준코드 미입력", { exact: true })).toBeVisible();
  await expect(page.getByText("attention 대표", { exact: true })).toBeVisible();
  await expect(page.getByText("priority 관측", { exact: true })).toBeVisible();
  await expect(page.getByText("current priority 승격 기준 · 3 감소", { exact: true })).toBeVisible();
  await expect(page.getByText("current priority 승격 기준 · 1건 · standard-codes / warning", { exact: true })).toBeVisible();
  await expect(page.getByText("표준코드 입력 backlog", { exact: true }).first()).toBeVisible();
  await expect(page.locator(attentionPrimaryAction(ADMIN_DASHBOARD_ATTENTION_KEYS.standardCodeBacklog)).first()).toBeVisible();
  await expect(page.getByText("active baseline 재사용 · skipped -> passed · attention ok", { exact: true })).toBeVisible();
  await expect(page.getByText("개선 신호", { exact: true })).toBeVisible();
  await expect(page.getByText(/표준코드 미입력 3 감소/)).toBeVisible();
  await expect(page.getByText(/priority 관측 skipped -> passed/)).toBeVisible();
  await expect(page.getByText("정리 필요", { exact: true })).toBeVisible();
  await expect(page.getByText("주시", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("정상", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("793", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("passed", { exact: true }).first()).toBeVisible();
});

test("admin dashboard 운영 알림 카드는 상위 주의 항목을 스크롤 없이 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const alertsCard = page.locator(section("admin-ops-alerts"));
  await expect(alertsCard.getByText("운영 알림", { exact: true })).toBeVisible();
  await expect(alertsCard.getByText(/지금 바로 볼 우선 신호 \d+건/)).toBeVisible();
  await expect(alertsCard.getByText("수집 drift 확인", { exact: true })).toBeVisible();
  await expect(alertsCard.getByText("표준코드 입력 backlog", { exact: true })).toBeVisible();
  await expect(alertsCard.getByText("stale 알림 target backlog", { exact: true })).toBeVisible();
  await expect(alertsCard.getByRole("button", { name: "주의 항목 큐 보기", exact: true })).toBeVisible();
});

test("admin dashboard 주의 항목 큐는 collect와 표준코드 backlog를 함께 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const queueSection = page.locator(section("admin-attention-queue"));
  await expect(queueSection.getByText("지금 먼저 볼 주의 항목", { exact: true })).toBeVisible();
  await expect(queueSection.getByText("수집 drift 확인", { exact: true })).toBeVisible();
  await expect(queueSection.getByText("표준코드 입력 backlog", { exact: true })).toBeVisible();
  await expect(queueSection.getByText("알림 backlog 확인", { exact: true })).toBeVisible();
  const actionCount = await queueSection.getByRole("button", { name: "해당 섹션 보기" }).count();
  expect(actionCount).toBeGreaterThanOrEqual(4);
});

test("admin dashboard attention 진입 버튼은 올바른 섹션과 focus target을 활성화한다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  await page.locator(attentionPrimaryAction(ADMIN_DASHBOARD_ATTENTION_KEYS.standardCodeBacklog)).click();
  await expect(page.locator(activeSection("admin-standard-code-coverage"))).toBeVisible();
  await expect(page.locator(activePrimaryFocusWithin("admin-standard-code-coverage"))).toBeVisible();

  const alertsCard = page.locator(section("admin-ops-alerts"));
  await alertsCard.getByRole("button", { name: "주의 항목 큐 보기", exact: true }).click();
  await expect(page.locator(activeSection("admin-attention-queue"))).toBeVisible();
  await expect(page.locator(activePrimaryFocusWithin("admin-attention-queue"))).toBeVisible();

  const queueSection = page.locator(section("admin-attention-queue"));
  await queueSection.locator(attentionAction(ADMIN_DASHBOARD_ATTENTION_KEYS.collectDrift)).click();
  await expect(page.locator(activeSection("admin-collect-triage"))).toBeVisible();
  await expect(page.locator(activeFocusWithin("admin-collect-triage", ADMIN_DASHBOARD_FOCUS_KEYS.default))).toBeVisible();

  await queueSection.locator(attentionAction(ADMIN_DASHBOARD_ATTENTION_KEYS.standardCodeBacklog)).click();
  await expect(page.locator(activeSection("admin-standard-code-coverage"))).toBeVisible();
  await expect(page.locator(activePrimaryFocusWithin("admin-standard-code-coverage"))).toBeVisible();
});

test("admin dashboard quick jump는 코드북/추천 상세 focus card를 활성화한다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpReferenceCodebooks)).click();
  await expect(page.locator(activeSection("admin-reference-codebooks"))).toBeVisible();
  await expect(page.locator(activeCard(ADMIN_DASHBOARD_CARD_KEYS.matchedRowCount))).toBeVisible();

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpRecommendationBreakdowns)).click();
  await expect(page.locator(activeSection("admin-recommendation-breakdowns"))).toBeVisible();
  await expect(page.locator(activeList(ADMIN_DASHBOARD_LIST_KEYS.topRepeatedServices))).toBeVisible();
});

test("admin dashboard quick jump는 개요/효과/wrapper focus card를 활성화한다 @admin-required", async ({ page }) => {
  test.setTimeout(150_000);
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpRecommendationOverview)).click();
  await expect(page.locator(activeSection("admin-recommendation-overview"))).toBeVisible();
  await expect(page.locator(activeCard(ADMIN_DASHBOARD_CARD_KEYS.top1LeaderSignal))).toBeVisible();

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpStandardCodeEffect)).click();
  await expect(page.locator(activeSection("admin-standard-code-effect"))).toBeVisible();
  await expect(page.locator(activeCard(ADMIN_DASHBOARD_CARD_KEYS.welfareScenarioCount))).toBeVisible();

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpWrapperObservation)).click();
  await expect(page.locator(activeSection("admin-wrapper-observation"))).toBeVisible();
  await expect(page.locator(activeCard(ADMIN_DASHBOARD_CARD_KEYS.currentPriorityMissingStandardCodes))).toBeVisible();
});

test("admin dashboard quick jump는 attention/coverage/collect/search 경로를 활성화한다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpAttentionQueue)).click();
  await expect(page.locator(activeSection("admin-attention-queue"))).toBeVisible();
  await expect(page.locator(activePrimaryFocusWithin("admin-attention-queue"))).toBeVisible();

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpStandardCodeCoverage)).click();
  await expect(page.locator(activeSection("admin-standard-code-coverage"))).toBeVisible();
  await expect(page.locator(activePrimaryFocusWithin("admin-standard-code-coverage"))).toBeVisible();

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpCollectTriage)).click();
  await expect(page.locator(activeSection("admin-collect-triage"))).toBeVisible();
  await expect(page.locator(activeFocusWithin("admin-collect-triage", ADMIN_DASHBOARD_FOCUS_KEYS.default))).toBeVisible();

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpSearchTriage)).click();
  await expect(page.locator(activeSection("admin-search-triage"))).toBeVisible();
  await expect(page.locator(activeFocusWithin("admin-search-triage", ADMIN_DASHBOARD_FOCUS_KEYS.searchWarning))).toBeVisible();
});

test("admin dashboard 수집/검색 jump 액션은 올바른 focus target을 활성화한다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginToAdminDashboard(page);

  const collectSection = page.locator(section("admin-collect-triage"));
  await collectSection.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.collectPartialView)).click();
  await expect(page.locator(activeList(ADMIN_DASHBOARD_LIST_KEYS.collectJobBreakdowns))).toBeVisible();
  await expect(page.locator(activeFocus(ADMIN_DASHBOARD_FOCUS_KEYS.collectPartial))).toBeVisible();

  await collectSection.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.collectCircuitView)).click();
  await expect(page.locator(activeList(ADMIN_DASHBOARD_LIST_KEYS.collectCircuitStatuses))).toBeVisible();
  await expect(page.locator(activeFocus(ADMIN_DASHBOARD_FOCUS_KEYS.collectCircuit))).toBeVisible();

  await collectSection.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.collectFailureSampleView)).click();
  await expect(page.locator(activeList(ADMIN_DASHBOARD_LIST_KEYS.collectRecentFailureSamples))).toBeVisible();
  await expect(page.locator(activeFocus(ADMIN_DASHBOARD_FOCUS_KEYS.default))).toBeVisible();

  const searchSection = page.locator(section("admin-search-triage"));
  await searchSection.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.searchWarningView)).click();
  await expect(page.locator(activeList(ADMIN_DASHBOARD_LIST_KEYS.searchRecentZeroResultSamples))).toBeVisible();
  await expect(page.locator(activeFocus(ADMIN_DASHBOARD_FOCUS_KEYS.searchWarning))).toBeVisible();

  await searchSection.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.searchRecoveredView)).click();
  await expect(page.locator(activeList(ADMIN_DASHBOARD_LIST_KEYS.searchRecoveredGroups))).toBeVisible();
  await expect(page.locator(activeFocus(ADMIN_DASHBOARD_FOCUS_KEYS.searchRecovered))).toBeVisible();
});

test("admin dashboard 운영 알림 카드는 warning 상태일 때 wrapper 경고까지 승격한다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await page.route("**/api/admin/dashboard/attention-feed*", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify(buildAdminDashboardApiPayload({
      generatedAt: adminDashboardFixtures.attentionFeed.generatedAt,
      itemCount: 5,
      items: [
        ...adminDashboardFixtures.attentionFeed.items,
        {
          key: "wrapper-warning",
          severity: "warning",
          title: "운영 주시 포인트",
          message: "표준코드 미입력 5 증가, priority 관측 passed -> failed",
          targetId: "admin-wrapper-observation",
          source: "wrapper-observation",
        },
      ],
    })),
  }));
  await page.route("**/api/admin/dashboard/wrapper-observation*", (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify(buildAdminDashboardApiPayload({
      ...adminDashboardFixtures.wrapperObservation,
      currentPriorityUsersMissingAllStandardCodes: 801,
      currentPriorityUsersMissingAllStandardCodesDelta: 5,
      currentPriorityUsersMissingAllStandardCodesDeltaLabel: "5 증가",
      currentPriorityRecommendationObservationStatus: "failed",
      currentPriorityPreviousRecommendationObservationStatus: "passed",
      currentPriorityRecommendationObservationStatusChanged: true,
      currentPriorityRecommendationObservationStatusTransitionLabel: "passed -> failed",
      promotedAlert: {
        severity: "warning",
        title: "운영 주시 포인트",
        message: "표준코드 미입력 5 증가, priority 관측 passed -> failed",
      },
    })),
  }));

  await loginToAdminDashboard(page);

  const alertsCard = page.locator(section("admin-ops-alerts"));
  await expect(alertsCard.getByText(/지금 바로 볼 우선 신호 \d+건/)).toBeVisible();
  await expect(alertsCard.getByText("운영 주시 포인트", { exact: true })).toBeVisible();
  await expect(alertsCard.getByText(/표준코드 미입력 5 증가/)).toBeVisible();
  await expect(alertsCard.getByText(/priority 관측 passed -> failed/)).toBeVisible();
  await expect(alertsCard.getByRole("button", { name: "주의 항목 큐 보기", exact: true })).toBeVisible();
  await alertsCard.locator(attentionAction(ADMIN_DASHBOARD_ATTENTION_KEYS.wrapperWarning)).click();
  await expect(page.locator(activeSection("admin-wrapper-observation"))).toBeVisible();
  await expect(page.locator(activePrimaryFocusWithin("admin-wrapper-observation"))).toBeVisible();
});
