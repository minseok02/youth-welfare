import { execFileSync } from "node:child_process";
import { expect } from "@playwright/test";

function clearLoginRateLimitKeys() {
  try {
    execFileSync("docker", [
      "exec",
      process.env.REDIS_CONTAINER_NAME || "youth-welfare-redis",
      "sh",
      "-lc",
      "redis-cli --scan --pattern 'auth:rate-limit:login:*' | xargs -r redis-cli DEL >/dev/null",
    ], {
      stdio: "ignore",
    });
  } catch {
    // Local smoke may run without docker-backed redis access; skip best-effort cleanup.
  }
}

export async function expectLoggedInChat(page) {
  await expect(page).toHaveURL(/\/chat(?:\?.*)?$/);
  await expect(page.getByRole("button", { name: "새 대화" })).toBeVisible();
}

export async function loginThroughForm(page, credentials) {
  clearLoginRateLimitKeys();
  await page.getByPlaceholder("example@email.com").fill(credentials.email);
  await page.getByPlaceholder("비밀번호를 입력하세요").fill(credentials.password);

  const loginResponsePromise = page.waitForResponse((response) =>
    response.request().method() === "POST" && response.url().includes("/api/auth/login")
  );

  await page.getByRole("button", { name: "로그인" }).click();

  const loginResponse = await loginResponsePromise;
  expect(loginResponse.ok()).toBeTruthy();
}

export async function loginFromProtectedRoute(page, protectedPath, credentials) {
  await page.goto(protectedPath);
  await loginThroughForm(page, credentials);
}
