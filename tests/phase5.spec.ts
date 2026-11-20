import { expect, test } from "@playwright/test";

test("written decisions remain usable without WebGL", async ({ page }) => {
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement, contextId, ...options) {
      if (contextId === "webgl" || contextId === "webgl2" || contextId === "experimental-webgl") return null;
      return Reflect.apply(original, this, [contextId, ...options]);
    } as typeof HTMLCanvasElement.prototype.getContext;
  });

  await page.goto("/");
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();
  await page.getByRole("button", { name: "Explore the existing demo data" }).click();

  const navigation = page.getByRole("navigation", { name: "Skill Stack results" });
  await navigation.getByRole("button", { name: "Paths", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Paths for you" })).toBeVisible();
  await expect(page.getByLabel("Selected path details")).toBeVisible();

  await navigation.getByRole("button", { name: "Develop", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Ways to develop" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Practical next steps" })).toBeVisible();

  await navigation.getByRole("button", { name: "Skill map", exact: true }).click();
  await expect(page.getByRole("region", { name: "Skill map unavailable" })).toBeVisible();
  await expect(page.getByText("Your written Skill Stack results and the structured list remain available.", { exact: false })).toBeVisible();
  await page.screenshot({ path: "public/captures/phase-5/02-no-webgl-fallback.png", fullPage: true });

  await navigation.getByRole("button", { name: "Paths", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Paths for you" })).toBeVisible();
});

test("private account controls have keyboard focus and restore it on Escape", async ({ page }) => {
  await page.goto("/");
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();
  await page.getByRole("button", { name: "Explore the existing demo data" }).click();

  const accountTrigger = page.getByRole("button", { name: /Open .* private account controls/ });
  await accountTrigger.focus();
  await accountTrigger.press("Enter");

  const dialog = page.getByRole("dialog", { name: "Your account" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Close account controls" })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(accountTrigger).toBeFocused();
});

test("an interrupted API request produces a safe retryable error", async ({ page }) => {
  await page.route("**/api/v1/auth/dev-login", (route) => route.abort("internetdisconnected"));
  await page.goto("/");
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();

  await page.getByLabel("How should we address you?").fill("Offline Explorer");
  await page.getByRole("button", { name: "Build my Skill Stack" }).click();

  await expect(page.locator(".inline-error")).toContainText("API is not reachable");
  await expect(page.getByRole("button", { name: "Build my Skill Stack" })).toBeEnabled();
});
