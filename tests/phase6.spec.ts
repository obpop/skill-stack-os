import { expect, test } from "@playwright/test";

test("signed-out screen is a simple app welcome with sign-in and no showcase content", async ({ page }) => {
  await page.goto("/");
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();

  await expect(page.getByRole("heading", { name: "Know the next skill worth building." })).toBeVisible();
  await expect(page.getByLabel("How should we address you?")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Works without AI" })).toBeVisible();
  await expect(page.getByText("backend/.env", { exact: true })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Site" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: /demo|thesis|case study|GitHub/i })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
});

test("showcase-only routes do not exist in the app", async ({ request }) => {
  for (const path of ["/demo", "/demo/develop", "/thesis", "/case-study", "/brand", "/running-locally"]) {
    expect((await request.get(path)).status()).toBe(404);
  }
});

test("owner can preview, publish, inspect and immediately unpublish a selective stack", async ({ page, context }) => {
  await page.goto("/");
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();
  await page.getByRole("button", { name: "Explore the existing demo data" }).click();
  await expect(page.getByRole("navigation", { name: "Skill Stack results" })).toBeVisible({ timeout: 15_000 });

  const accountTrigger = page.getByRole("button", { name: /Open .* private account controls/ });
  await accountTrigger.click();
  await page.getByRole("dialog", { name: "Your account" }).getByRole("button", { name: "Preview and publish my Skill Stack" }).click();

  const panel = page.getByRole("dialog", { name: "Choose exactly what becomes public" });
  await expect(panel).toBeVisible();
  await expect(panel.getByRole("button", { name: "Close public profile controls" })).toBeFocused();
  await expect(panel.getByText("Everything stays private unless enabled here and included in the preview.")).toBeVisible();
  await expect(panel.getByText(/Private role, city, goals, imports, rejected ideas/)).toBeVisible();

  const firstSkill = panel.locator(".selection-list").first().getByRole("checkbox").first();
  if (!(await firstSkill.isChecked())) await firstSkill.check();
  await panel.getByRole("button", { name: "Save and refresh private preview" }).click();
  await expect(panel.locator(".mini-skills span").first()).toBeVisible();
  await panel.getByRole("button", { name: /Publish this snapshot|Republish reviewed snapshot/ }).click();

  const publicLink = panel.getByRole("link", { name: "Open public profile" });
  await expect(publicLink).toBeVisible();
  const publicPath = await publicLink.getAttribute("href");
  expect(publicPath).toMatch(/^\/public\//);
  const publicPage = await context.newPage();
  await publicPage.goto(publicPath!);
  await expect(publicPage.getByText("A selective public Skill Stack")).toBeVisible();
  await expect(publicPage.getByRole("heading", { name: "What this page does not claim" })).toBeVisible();
  await publicPage.close();

  await panel.getByRole("button", { name: "Unpublish now" }).click();
  await expect(panel.getByText("Not public", { exact: true })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(panel).toBeHidden();
  await expect(accountTrigger).toBeFocused();
});

async function expectNoHorizontalOverflow(page: import("@playwright/test").Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
}
