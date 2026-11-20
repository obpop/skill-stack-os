import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/?mode=phase1");
  await expect(page.getByRole("button", { name: "Try a constellation" })).toBeVisible();
  (page as unknown as { __errors: string[] }).__errors = errors;
});

test.afterEach(async ({ page }) => {
  expect((page as unknown as { __errors?: string[] }).__errors ?? []).toEqual([]);
});

test("signature discovery choreography", async ({ page }) => {
  await page.screenshot({ path: "public/captures/01-universe.png", fullPage: true });
  await page.getByRole("button", { name: "Try a constellation" }).click();
  await expect(page.getByText("A skill combination is forming")).toBeVisible();
  await page.waitForTimeout(400);
  await page.screenshot({ path: "public/captures/04-multi-selection.png", fullPage: true });
  await page.getByRole("button", { name: /Discover intersections/ }).click();
  await expect(page.getByText("Following relationships through your universe")).toBeVisible({ timeout: 2500 });
  await page.screenshot({ path: "public/captures/05-discover-traversal.png", fullPage: true });
  await expect(page.getByRole("heading", { name: "Financial Intelligence Systems" })).toBeVisible({ timeout: 5000 });
  await page.waitForTimeout(500);
  await page.screenshot({ path: "public/captures/06-discovered-intersection.png", fullPage: true });
  await page.getByRole("button", { name: "Save intersection" }).click();
  await expect(page.getByRole("button", { name: "Saved to universe" })).toBeDisabled();
  await page.getByRole("button", { name: /Return to Skill Stack/ }).click();
});

test("search focuses a concept and opens its neighborhood", async ({ page }) => {
  const search = page.getByRole("textbox", { name: "Search your Skill Stack" });
  await search.fill("RabbitMQ");
  await page.getByRole("option", { name: /RabbitMQ/ }).click();
  await expect(page.getByRole("heading", { name: "RabbitMQ" })).toBeVisible();
  await expect(page.getByText("Immediate network")).toBeVisible();
  await page.waitForTimeout(500);
  await page.screenshot({ path: "public/captures/03-search-focus.png", fullPage: true });
});

test("canvas nodes support practical keyboard traversal", async ({ page }) => {
  const canvas = page.getByRole("application", { name: /Interactive Skill Stack map/ });
  await canvas.focus();
  await canvas.press("Home");
  const announcement = page.locator(".sr-only[role='status']");
  await expect(announcement).not.toHaveText("");
  const first = await announcement.textContent();
  await canvas.press("ArrowRight");
  await expect(announcement).not.toHaveText(first ?? "");
  await canvas.press("Enter");
  await expect(page.locator("aside.node-sheet")).toBeVisible();
});

test("structured directory exposes status and relationship context", async ({ page }) => {
  await page.getByRole("button", { name: "Open structured graph directory" }).click();
  const directory = page.getByRole("dialog", { name: "Structured graph directory" });
  await expect(directory.getByText(/relationship count/)).toBeVisible();
  await expect(directory.locator("summary").filter({ hasText: "Skills" })).toBeVisible();
  const java = directory.getByRole("button", { name: /^Backend development with Spring Boot,/ });
  await expect(java).toHaveAccessibleName(/connections/);
  await java.click();
  await expect(page.getByRole("heading", { name: "Backend development with Spring Boot" })).toBeVisible();
});

test("normal mode exposes the focused three-type filter", async ({ page }) => {
  await page.getByRole("button", { name: /Filter$/ }).click();
  const filters = page.getByLabel("Graph filters");
  await expect(filters.getByRole("button", { name: "skill" })).toBeVisible();
  await expect(filters.getByRole("button", { name: "interest" })).toBeVisible();
  await expect(filters.getByRole("button", { name: "project" })).toBeVisible();
  await expect(filters.getByText("Worlds")).toHaveCount(0);
  await page.screenshot({ path: "public/captures/02-cluster-focus.png", fullPage: true });
});

test("projects remain in the graph as proof", async ({ page }) => {
  const search = page.getByRole("textbox", { name: "Search your Skill Stack" });
  await search.fill("Distributed Systems Lab");
  await page.getByRole("option", { name: /Distributed Systems Lab/ }).click();
  await expect(page.getByText("Evidence attached")).toBeVisible();
  await page.waitForTimeout(500);
  await page.screenshot({ path: "public/captures/07-project-connection.png", fullPage: true });
});

test("mobile uses a focused sheet rather than shrinking desktop chrome", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/?mode=phase1");
  await expect(page.getByRole("textbox", { name: "Search your Skill Stack" })).toBeVisible();
  await page.getByRole("button", { name: "Try a constellation" }).click();
  await expect(page.getByRole("button", { name: /Discover intersections/ })).toBeVisible();
});

test("reduced motion completes discovery without waiting for decorative animation", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/?mode=phase1");
  await page.getByRole("button", { name: "Try a constellation" }).click();
  await page.getByRole("button", { name: /Discover intersections/ }).click();
  await expect(page.getByRole("heading", { name: "Financial Intelligence Systems" })).toBeVisible({ timeout: 1200 });
});
