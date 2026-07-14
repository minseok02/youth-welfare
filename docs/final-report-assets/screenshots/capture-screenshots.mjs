import playwright from "../../../frontend/node_modules/playwright/index.js";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const { chromium } = playwright;

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = __dirname;

const appBaseUrl = process.env.REPORT_SCREENSHOT_APP_URL || "http://127.0.0.1:5173";
const apiBaseUrl = process.env.REPORT_SCREENSHOT_API_URL || "http://127.0.0.1:8082";
const email = process.env.REPORT_SCREENSHOT_EMAIL;
const password = process.env.REPORT_SCREENSHOT_PASSWORD;

if (!email || !password) {
  throw new Error("Set REPORT_SCREENSHOT_EMAIL and REPORT_SCREENSHOT_PASSWORD before regenerating login screenshots.");
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function apiJson(pathname, options = {}) {
  const response = await fetch(`${apiBaseUrl}${pathname}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    // Keep raw text for debugging.
  }
  return { response, json, text };
}

async function prepareUserData() {
  const login = await apiJson("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
  const token = login.json?.data?.accessToken;
  if (!token) {
    throw new Error(`API login failed for screenshot user: ${login.text}`);
  }

  const authHeaders = { Authorization: `Bearer ${token}` };

  await apiJson("/api/users/me", {
    method: "PUT",
    headers: authHeaders,
    body: JSON.stringify({
      name: "보고서사용자",
      birthDate: "2000-05-10",
      sido: "서울특별시",
      sgg: "관악구",
      incomeLevel: 4,
      employmentStatus: "재학중",
      householdType: "1인가구",
      optionalProfileConsentAgreed: true,
      sensitiveInfoConsentAgreed: false,
      interestFields: ["주거", "교육·직업훈련", "일자리"],
      targetTypes: ["청년"],
      notificationYn: true,
      notificationEmailYn: false,
      notificationInAppYn: true,
      notificationWebPushYn: false,
      notificationPeriod: "DAILY",
      notificationMinScore: 0.5,
      displayCount: 10,
    }),
  });

  await apiJson("/api/users/me/priorities", {
    method: "PUT",
    headers: authHeaders,
    body: JSON.stringify({
      priorityCodes: ["HOUSING", "EDUCATION", "JOB"],
      optionalProfileConsentAgreed: true,
    }),
  });

  await Promise.race([
    apiJson("/api/recommendations/refresh", {
      method: "POST",
      headers: authHeaders,
      body: "{}",
    }),
    sleep(20_000).then(() => null),
  ]);

  await Promise.race([
    apiJson("/api/notifications/digest-test-dispatch", {
      method: "POST",
      headers: authHeaders,
      body: "{}",
    }),
    sleep(10_000).then(() => null),
  ]);

  const policies = await apiJson("/api/policies?page=0&size=1&sort=LATEST");
  const policyId = policies.json?.data?.content?.[0]?.id ?? 10449;
  return { token, policyId };
}

async function stabilize(page) {
  await page.addStyleTag({
    content: `
      *, *::before, *::after {
        transition-duration: 0s !important;
        animation-duration: 0s !important;
        animation-delay: 0s !important;
        scroll-behavior: auto !important;
      }
      body { background: #f7f8fc !important; }
    `,
  }).catch(() => {});
}

async function waitForSettledPage(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await stabilize(page);
  await sleep(900);
}

async function gotoAndShot(page, pathname, file, options = {}) {
  await page.goto(`${appBaseUrl}${pathname}`, { waitUntil: "domcontentloaded" });
  await waitForSettledPage(page);
  if (options.beforeShot) {
    await options.beforeShot(page);
    await waitForSettledPage(page);
  }
  await page.screenshot({
    path: path.join(outDir, file),
    fullPage: Boolean(options.fullPage),
  });
  console.log(`wrote ${file}`);
}

async function spaAndShot(page, pathname, file, options = {}) {
  await page.evaluate((targetPath) => {
    window.history.pushState({}, "", targetPath);
    window.dispatchEvent(new PopStateEvent("popstate"));
  }, pathname);
  await waitForSettledPage(page);
  if (options.beforeShot) {
    await options.beforeShot(page);
    await waitForSettledPage(page);
  }
  await page.screenshot({
    path: path.join(outDir, file),
    fullPage: Boolean(options.fullPage),
  });
  console.log(`wrote ${file}`);
}

async function loginInUi(page) {
  await page.goto(`${appBaseUrl}/login`, { waitUntil: "domcontentloaded" });
  await waitForSettledPage(page);
  const emailInput = page.locator('form input[type="email"]');
  const passwordInput = page.locator('form input[type="password"]');
  const submitButton = page.locator('form button[type="submit"]');
  await emailInput.waitFor({ state: "visible", timeout: 15_000 });
  await emailInput.fill(email);
  await passwordInput.fill(password);
  await submitButton.waitFor({ state: "visible", timeout: 15_000 });
  await submitButton.click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 15_000 });
  await waitForSettledPage(page);
}

await fs.writeFile(path.join(outDir, "README.md"), `# 결과보고서 구현 화면 캡처

재생성 전제:

- 백엔드 API: ${apiBaseUrl}
- 프론트엔드: ${appBaseUrl}

재생성:

\`\`\`bash
REPORT_SCREENSHOT_EMAIL=... REPORT_SCREENSHOT_PASSWORD=... node docs/final-report-assets/screenshots/capture-screenshots.mjs
\`\`\`
`);

const { policyId } = await prepareUserData();

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({
  viewport: { width: 1440, height: 1050 },
  deviceScaleFactor: 1,
});

await gotoAndShot(page, "/", "screen-08-main.png");
await gotoAndShot(page, "/policies", "screen-09-policy-search.png");
await gotoAndShot(page, `/policies/${policyId}`, "screen-10-policy-detail.png");

await loginInUi(page);

await spaAndShot(page, "/mypage", "screen-11-mypage-profile-priority.png");
await spaAndShot(page, "/", "screen-12-recommendations.png", {
  beforeShot: async (targetPage) => {
    await targetPage.evaluate(() => window.scrollTo(0, 760));
    await sleep(1_000);
  },
});
await spaAndShot(page, "/chat", "screen-13-chatbot.png", {
  beforeShot: async (targetPage) => {
    const input = targetPage.getByPlaceholder("예: 서울에 사는 미취업 청년인데 월세나 교육비 지원을 같이 보고 싶어요.");
    if (await input.count()) {
      await input.fill("서울에 사는 청년인데 월세 지원 정책을 알려줘");
      await targetPage.getByRole("button", { name: /질문 보내기|전송 중/ }).click();
      await targetPage.getByText("질문에 맞는 정책을 찾는 중입니다...").waitFor({ timeout: 10_000 }).catch(() => {});
      await targetPage.getByText("질문에 맞는 정책을 찾는 중입니다...").waitFor({ state: "hidden", timeout: 45_000 }).catch(() => {});
      await sleep(1_500);
    }
  },
});
await spaAndShot(page, "/alerts", "screen-14-alerts.png");

await browser.close();
