import { defineConfig } from "@playwright/test";
import { existsSync } from "node:fs";

const windowsEdge = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const windowsChrome = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";
const windowsBrowser = existsSync(windowsEdge) ? windowsEdge : windowsChrome;

export default defineConfig({
  testDir: "./tests",
  testMatch: "**/*.spec.ts",
  timeout: 30_000,
  use: {
    baseURL: "http://127.0.0.1:3100",
    viewport: { width: 1440, height: 900 },
    colorScheme: "dark",
    trace: "retain-on-failure",
    launchOptions: process.platform === "win32" && existsSync(windowsBrowser) ? { executablePath: windowsBrowser } : undefined,
  },
  webServer: {
    command: "npm run start -- -p 3100",
    url: "http://127.0.0.1:3100",
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
