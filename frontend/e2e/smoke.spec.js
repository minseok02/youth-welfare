import { expect, test } from "@playwright/test";
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

test("정책 상세는 요약 정보와 오류 제보 CTA를 보여준다", async ({ page, request }) => {
  const firstPolicy = await openFirstSearchResult(page, request, "월세");

  await expect(page.getByText(firstPolicy.title, { exact: true }).first()).toBeVisible();
  await expect(page.getByText("요약 정보", { exact: true })).toBeVisible();
  await expect(page.getByText("지원지역", { exact: true })).toBeVisible();
  await expect(page.getByText("소관기관", { exact: true })).toBeVisible();
  await expect(page.getByText("신청기간", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "⚑ 정책 오류 제보", exact: true })).toBeVisible();
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

  await expect(page.getByText("문의가 접수되었습니다. 확인 후 답변드리겠습니다.", { exact: true })).toBeVisible();
});

test("비로그인 정책 상세 오류 제보는 로그인으로 분기한다", async ({ page, request }) => {
  const firstPolicy = await openFirstSearchResult(page, request, "청년");
  await expect(page.getByRole("button", { name: "⚑ 정책 오류 제보", exact: true })).toBeVisible();

  await page.getByRole("button", { name: "⚑ 정책 오류 제보", exact: true }).click();
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

  const firstPolicy = await openFirstSearchResult(page, request, "월세");
  await expect(page.getByText(firstPolicy.title, { exact: true }).first()).toBeVisible();
  await expect(page.getByText("요약 정보", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "⚑ 정책 오류 제보", exact: true })).toBeVisible();
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
  await page.goto("/");
  await expect(page.getByText("추천 품질 우선 개선", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "맞춤 재추천 →", exact: true }).click();
  await expect(page).toHaveURL(/\/mypage\?tab=1$/);
});

test("메인 개인 맞춤 재추천 CTA는 표준코드 공백이 크면 마이페이지 내 정보 탭으로 이동한다", async ({ page }) => {
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
  await page.goto("/");
  await expect(page.getByText("추천 정확도 보강", { exact: true }).first()).toBeVisible();
  await page.getByRole("button", { name: "맞춤 재추천 →", exact: true }).click();
  await expect(page).toHaveURL(/\/mypage\?tab=0$/);
});

test("로그인 직후 메인에서는 우선순위와 표준코드 공백에 대한 추천 nudge를 한 번 보여준다", async ({ page }) => {
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

  await expect(page.getByText("로그인되었습니다. 우선순위와 표준코드 1/4 입력 상태를 함께 채우면 추천 품질이 더 빨리 안정됩니다.")).toBeVisible();
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

  await page.getByRole("button", { name: "이용가이드 보기 →", exact: true }).click();
  await expect(page).toHaveURL(/\/guide$/);

  await page.goBack();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByText("추천 품질 우선 개선", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "맞춤 재추천 →", exact: true }).click();
  await expect(page).toHaveURL(/\/mypage\?tab=1$/);
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

test("알림함 빈 상태 CTA는 정책 목록과 알림 설정으로 이어진다", async ({ page }) => {
  await mockAlertsApis(page, { alerts: [], unreadCount: 0 });

  await loginFromProtectedRoute(page, "/alerts", userCredentials);
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

test("알림함에서 unread 알림을 열면 읽음 처리 후 deeplink로 이동한다", async ({ page }) => {
  let readPatchCount = 0;

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
  await page.getByRole("button", { name: "열기", exact: true }).click();

  expect(readPatchCount).toBe(1);
  await expect(page).toHaveURL(/\/policies\/7751$/);
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
  await expect(page.locator(section("admin-recommendation-overview")).getByText("추천 검토 상태", { exact: true }).first()).toBeVisible();
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
  await expect(page.locator(section("admin-recommendation-overview")).getByText("추천 검토 상태", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("일부 섹션만 불러오지 못했습니다.")).toBeVisible();
  await expect(page.getByText("추천 상세 진단 로드 실패")).toBeVisible();
  await expect(page.getByText("breakdown forced failure")).toBeVisible();
});

test("admin dashboard 공식 코드북 탐색은 검색과 메타데이터를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

  await expect(page.getByText("공식 코드북 탐색", { exact: true })).toBeVisible();
  await expect(page.getByText("행 조회 가능", { exact: true })).toBeVisible();
  await expect(page.getByText("아파트", { exact: true })).toBeVisible();
  await expect(page.getByText("다가구주택", { exact: true })).toBeVisible();

  await page.getByPlaceholder("코드값, 라벨, 설명 검색").fill("아파트");
  await expect(page.getByText("다가구주택", { exact: true })).toHaveCount(0);
  await expect(page.getByText("아파트", { exact: true })).toBeVisible();

  await page.getByRole("combobox").nth(1).click();
  await page.getByRole("option", { name: "LOCAL_AGENCY_CODES", exact: true }).click();

  await expect(page.getByText("메타데이터 전용", { exact: true })).toBeVisible();
  await expect(page.getByText(/activeRowCount: 135186/)).toBeVisible();
  await expect(page.getByText("행정안전부", { exact: true })).toBeVisible();
});

test("admin dashboard 표준코드 입력률 섹션은 coverage를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

  const supportInquiriesSection = page.locator(section("admin-support-inquiries"));

  await expect(supportInquiriesSection.getByText("서비스 문의 recent queue", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("열린 문의", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("최근 24시간 신규", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("표시 문의", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("열린 건", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("처리완료", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("전체", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("1", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("추천/챗봇", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("챗봇이 이전 질문 맥락을 잘 못 이어갑니다.", { exact: true })).toBeVisible();
  await expect(supportInquiriesSection.getByText("정책 검색/필터", { exact: true }).first()).toBeVisible();
  await expect(supportInquiriesSection.getByText("필터가 바로 적용되는지 헷갈립니다.", { exact: true })).toBeVisible();
});

test("admin dashboard 정책 중복 review 섹션은 duplicate queue를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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

test("admin dashboard stale notification target 섹션은 오래된 unread target cluster를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await expect(staleTargetSection.getByText("11", { exact: true }).first()).toBeVisible();
  await expect(staleTargetSection.getByText("4", { exact: true }).first()).toBeVisible();
  await expect(staleTargetSection.getByText("북마크한 정책 신청 마감 7일 전이에요", { exact: true })).toBeVisible();
  await expect(staleTargetSection.getByRole("button", { name: "7일 초과 숨기기" }).first()).toBeVisible();
});

test("admin dashboard 표준코드 추천 효과 섹션은 matrix 결과를 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

  const alertsCard = page.locator(section("admin-ops-alerts"));
  await expect(alertsCard.getByText("운영 알림", { exact: true })).toBeVisible();
  await expect(alertsCard.getByText(/지금 바로 볼 우선 신호 4건/)).toBeVisible();
  await expect(alertsCard.getByText("수집 drift 확인", { exact: true })).toBeVisible();
  await expect(alertsCard.getByText("표준코드 입력 backlog", { exact: true })).toBeVisible();
  await expect(alertsCard.getByText("정책 중복 review backlog", { exact: true })).toBeVisible();
  await expect(alertsCard.getByRole("button", { name: "주의 항목 큐 보기", exact: true })).toBeVisible();
});

test("admin dashboard 주의 항목 큐는 collect와 표준코드 backlog를 함께 보여준다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

  const queueSection = page.locator(section("admin-attention-queue"));
  await expect(queueSection.getByText("지금 먼저 볼 주의 항목", { exact: true })).toBeVisible();
  await expect(queueSection.getByText("수집 drift 확인", { exact: true })).toBeVisible();
  await expect(queueSection.getByText("표준코드 입력 backlog", { exact: true })).toBeVisible();
  await expect(queueSection.getByText("알림 backlog 확인", { exact: true })).toBeVisible();
  await expect(queueSection.getByRole("button", { name: "해당 섹션 보기" })).toHaveCount(4);
});

test("admin dashboard attention 진입 버튼은 올바른 섹션과 focus target을 활성화한다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpReferenceCodebooks)).click();
  await expect(page.locator(activeSection("admin-reference-codebooks"))).toBeVisible();
  await expect(page.locator(activeCard(ADMIN_DASHBOARD_CARD_KEYS.matchedRowCount))).toBeVisible();

  await page.locator(adminAction(ADMIN_DASHBOARD_ACTION_KEYS.quickJumpRecommendationBreakdowns)).click();
  await expect(page.locator(activeSection("admin-recommendation-breakdowns"))).toBeVisible();
  await expect(page.locator(activeList(ADMIN_DASHBOARD_LIST_KEYS.topRepeatedServices))).toBeVisible();
});

test("admin dashboard quick jump는 개요/효과/wrapper focus card를 활성화한다 @admin-required", async ({ page }) => {
  await mockAdminDashboardApis(page);
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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
  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

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

  await loginFromProtectedRoute(page, "/admin/dashboard", adminCredentials);

  const alertsCard = page.locator(section("admin-ops-alerts"));
  await expect(alertsCard.getByText(/지금 바로 볼 우선 신호 5건/)).toBeVisible();
  await expect(alertsCard.getByText("운영 주시 포인트", { exact: true })).toBeVisible();
  await expect(alertsCard.getByText(/표준코드 미입력 5 증가/)).toBeVisible();
  await expect(alertsCard.getByText(/priority 관측 passed -> failed/)).toBeVisible();
  await expect(alertsCard.getByRole("button", { name: "주의 항목 큐 보기", exact: true })).toBeVisible();
  await alertsCard.locator(attentionAction(ADMIN_DASHBOARD_ATTENTION_KEYS.wrapperWarning)).click();
  await expect(page.locator(activeSection("admin-wrapper-observation"))).toBeVisible();
  await expect(page.locator(activePrimaryFocusWithin("admin-wrapper-observation"))).toBeVisible();
});
