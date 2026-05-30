import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { adminDashboardFixtures, buildAdminDashboardApiPayload } from "./fixtures/adminDashboard.js";
import { expectLoggedInChat, loginFromProtectedRoute, loginThroughForm } from "./support/auth.js";
import { resolveAdminCredentials, resolveUserCredentials } from "./support/env.js";

const userCredentials = resolveUserCredentials();
const adminCredentials = resolveAdminCredentials();
const apiBaseUrl = process.env.VITE_API_BASE_URL || "http://127.0.0.1:8082";
const adminDashboardE2EFailureStorageKey = "__ADMIN_DASHBOARD_E2E_FAIL__";

async function searchPolicies(page, keyword) {
  await page.goto("/policies");
  await page.getByPlaceholder("정책명, 키워드를 검색해보세요 (예: 월세, 창업)").fill(keyword);
  await page.getByRole("button", { name: "검색", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/policies\\?[^#]*search=${encodeURIComponent(keyword)}`));
  await expect(page.getByRole("progressbar")).toHaveCount(0);
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
  const firstPolicy = await fetchFirstSearchResult(request, keyword);
  await page.getByText(firstPolicy.title, { exact: true }).first().click();
  await expect(page).toHaveURL(new RegExp(`/policies/${firstPolicy.id}`));
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

function resolvePasswordResetToken(email) {
  return execFileSync("bash", ["./scripts/resolve-password-reset-token.sh", email], {
    cwd: process.cwd(),
    encoding: "utf8",
  }).trim();
}

function issueDirectPasswordResetToken(email) {
  return execFileSync("bash", ["./scripts/issue-password-reset-token.sh", email], {
    cwd: process.cwd(),
    encoding: "utf8",
  }).trim();
}

async function signupUser(request, { email, password, name = "리셋테스트" }) {
  seedVerifiedEmail(email);
  const response = await request.post(`${apiBaseUrl}/api/auth/signup`, {
    data: {
      email,
      password,
      name,
      birthDate: "1999-02-10",
      sido: "서울특별시",
      sgg: "중구",
      incomeLevel: 5,
      employmentStatus: "미취업",
      householdType: "1인 가구",
    },
  });
  expect(response.ok()).toBeTruthy();
}

async function issuePasswordReset(request, email) {
  const requestResetResponse = await request.post(`${apiBaseUrl}/api/auth/password-reset/request`, {
    data: { email },
  });

  if (!requestResetResponse.ok()) {
    const payload = await requestResetResponse.json().catch(() => null);
    const errorCode = payload?.errorCode ?? payload?.code ?? payload?.data?.errorCode;
    if (errorCode === "A009") {
      const directToken = issueDirectPasswordResetToken(email);
      expect(directToken).toBeTruthy();
      return directToken;
    }
  }
  expect(requestResetResponse.ok()).toBeTruthy();

  const resetToken = resolvePasswordResetToken(email);
  expect(resetToken).toBeTruthy();
  return resetToken;
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
  ];

  await Promise.all(routes.map(([url, data]) => page.route(url, (route) => route.fulfill({
    status: 200,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify(buildAdminDashboardApiPayload(data)),
  }))));
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

  await loginFromProtectedRoute(page, "/chat", userCredentials);
  await expectLoggedInChat(page);

  await page.route("**/api/chat/sessions", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 401,
        contentType: "application/json; charset=utf-8",
        body: JSON.stringify({ success: false, errorCode: "A001", message: "unauthorized" }),
      });
      return;
    }
    await route.continue();
  });

  await page.route("**/api/auth/refresh", async (route) => {
    refreshAttemptCount += 1;
    await route.fulfill({
      status: 401,
      contentType: "application/json; charset=utf-8",
      body: JSON.stringify({ success: false, errorCode: "A003", message: "refresh expired" }),
    });
  });

  await page.getByRole("button", { name: "새 대화" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByPlaceholder("example@email.com")).toBeVisible();
  expect(refreshAttemptCount).toBe(1);

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
  await expect(page.getByText(firstPolicy.title, { exact: true }).first()).toBeVisible();

  await page.getByText(firstPolicy.title, { exact: true }).first().click();
  await expect(page).toHaveURL(new RegExp(`/policies/${firstPolicy.id}`));
  await page.getByRole("button", { name: /뒤로가기/ }).click();

  await expect(page).toHaveURL(/\/policies\?[^#]*search=%EC%B2%AD%EB%85%84/);
  await expect(page.getByText(firstPolicy.title, { exact: true }).first()).toBeVisible();
});

test("마이페이지 북마크 탭에서 상세로 갔다가 뒤로오면 tab query가 유지된다", async ({ page, request }) => {
  const policy = await fetchFirstSearchResult(request, "청년");
  await ensurePolicyBookmarked(request, userCredentials, policy.id);

  await loginFromProtectedRoute(page, "/mypage?tab=2", userCredentials);
  await expect(page).toHaveURL(/\/mypage\?tab=2$/);
  await expect(page.getByText("북마크한 정책")).toBeVisible();
  await expect(page.getByText(policy.title, { exact: true }).first()).toBeVisible();

  await page.getByText(policy.title, { exact: true }).first().click();
  await expect(page).toHaveURL(new RegExp(`/policies/${policy.id}`));
  await page.getByRole("button", { name: /뒤로가기/ }).click();

  await expect(page).toHaveURL(/\/mypage\?tab=2$/);
  await expect(page.getByText(policy.title, { exact: true }).first()).toBeVisible();
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
  await expect(page.getByRole("button", { name: "♡ 북마크에 저장" })).toBeVisible();
  await page.getByRole("button", { name: "♡ 북마크에 저장" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await loginThroughForm(page, userCredentials);

  await expect(page).toHaveURL(new RegExp(`/policies/${policy.id}`));
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

  const resetToken = await issuePasswordReset(request, email);

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

  const resetToken = await issuePasswordReset(request, email);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

  await expect(page.getByText("운영 추천 대시보드")).toBeVisible();
  await expect(page.locator("#admin-recommendation-overview").getByText("추천 검토 게이트", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("수집 실패 상세 로딩 중")).toHaveCount(0);
  await expect(page.getByText("수집 실패 상세", { exact: true })).toBeVisible();
  await expect(page.getByText("최근 0건 검색 키워드", { exact: true })).toBeVisible();
});

test("admin dashboard quick jump는 recommendation breakdown 섹션으로 이동한다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);
  await expect(page.getByText("운영 추천 대시보드")).toBeVisible();
  await page.getByRole("button", { name: /추천 상세/ }).click();
  await expect(page.getByText("반복 노출 상위 서비스")).toBeVisible();
  await expect(page.getByText("1순위 분포 선두 서비스")).toBeVisible();
});

test("admin dashboard summary 실패 시 collect/search triage는 유지된다 @dev-only", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await primeAdminDashboardFailureMode(page, "summary");
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

  await expect(page.getByText("추천 상세 진단 로딩 중")).toHaveCount(0);
  await expect(page.getByText("운영 추천 대시보드")).toBeVisible();
  await expect(page.locator("#admin-recommendation-overview").getByText("추천 검토 게이트", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("일부 섹션만 불러오지 못했습니다.")).toBeVisible();
  await expect(page.getByText("추천 상세 진단 로드 실패")).toBeVisible();
  await expect(page.getByText("breakdown forced failure")).toBeVisible();
});
