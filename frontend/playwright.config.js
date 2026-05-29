import path from "node:path";
import { defineConfig } from "@playwright/test";

const baseURL = process.env.PLAYWRIGHT_BASE_URL || "http://127.0.0.1:5173";
const apiBaseUrl = process.env.VITE_API_BASE_URL || "http://127.0.0.1:8082";
const localLibDir = process.env.PLAYWRIGHT_LD_LIBRARY_PATH
  || path.resolve(process.cwd(), "../.tmp/playwright-libs/rootfs/usr/lib/x86_64-linux-gnu");
const launchLdLibraryPath = [localLibDir, process.env.LD_LIBRARY_PATH].filter(Boolean).join(":");

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 960 },
    launchOptions: {
      env: {
        ...process.env,
        LD_LIBRARY_PATH: launchLdLibraryPath,
      },
    },
  },
  webServer: process.env.PLAYWRIGHT_SKIP_WEBSERVER === "true" ? undefined : {
    command: "npm run dev -- --host 127.0.0.1 --port 5173",
    url: baseURL,
    reuseExistingServer: true,
    timeout: 120 * 1000,
    env: {
      ...process.env,
      VITE_API_BASE_URL: apiBaseUrl,
    },
  },
  projects: [
    {
      name: "chromium",
      use: {
        browserName: "chromium",
      },
    },
  ],
});
