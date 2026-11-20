import { expect, test, type Page } from "@playwright/test";

const captures = "public/captures/phase-2.5";

test.describe.configure({ mode: "serial" });

test("a real user completes the Phase 2.5 journey and returns to the same private stack", async ({ page }) => {
  test.setTimeout(120_000);
  const browserErrors: string[] = [];
  page.on("console", (message) => { if (message.type() === "error") browserErrors.push(message.text()); });
  page.on("pageerror", (error) => browserErrors.push(error.message));

  await openFreshLogin(page);
  await page.screenshot({ path: `${captures}/01-welcome.png`, fullPage: true });

  await page.getByLabel("How should we address you?").fill("Phase 2.5 Explorer");
  await page.getByRole("button", { name: "Build my Skill Stack" }).click();
  await expect(page.getByRole("heading", { name: /See how your skills combine/ })).toBeVisible();
  await page.getByRole("button", { name: "Start" }).click();

  await expect(page.getByRole("heading", { name: "Make this stack yours." })).toBeVisible();
  await page.getByLabel("City").fill("Stockholm");
  await page.getByLabel("Country").fill("Sweden");
  await page.getByRole("button", { name: "Next" }).click();

  await expect(page.getByRole("heading", { name: "What can you do?" })).toBeVisible();
  const skillEntry = page.getByPlaceholder("e.g. Backend development with Spring Boot, social media content creation");
  await skillEntry.fill("Spring Boot");
  await page.getByRole("button", { name: "Add skill" }).click();
  await skillEntry.fill("TypeScript");
  await page.getByRole("button", { name: "Add skill" }).click();
  const javaDraft = page.locator(".draft-list article").filter({ hasText: "Spring Boot" });
  await javaDraft.getByLabel("Level").selectOption("GOOD");
  await javaDraft.getByLabel("Someone would pay for this").check();
  await page.screenshot({ path: `${captures}/02-skills.png`, fullPage: true });

  await page.locator('input[type="file"]').setInputFiles({
    name: "phase-2.5-profile.md",
    mimeType: "text/markdown",
    buffer: Buffer.from("Spring Boot and Python.\nProject: Skill Stack Launchpad"),
  });
  const importReview = page.getByLabel("Review imported suggestions");
  await expect(importReview).toBeVisible();
  await expect(importReview.locator('input[value="Python"]')).toBeVisible();
  await importReview.getByLabel("Level for Spring", { exact: true }).selectOption("TOP_25");
  await page.screenshot({ path: `${captures}/03-import-review.png`, fullPage: true });
  await importReview.getByRole("button", { name: /Add \d+ selected items/ }).click();
  await expect(page.getByText(/reviewed items added or merged/)).toBeVisible();
  await page.getByRole("button", { name: "Next" }).click();

  await expect(page.getByRole("heading", { name: "Where should this stack take you?" })).toBeVisible();
  const targetRole = page.locator("fieldset").filter({ hasText: "2. Where do you want to go?" });
  await targetRole.getByRole("button", { name: "Freelancer" }).click();
  const skillDirection = page.locator("fieldset").filter({ hasText: "4. Where do you want to go with your skills?" });
  await skillDirection.getByRole("combobox").selectOption("COMBINE");
  await skillDirection.getByLabel("Spring Boot").check();
  await skillDirection.getByLabel("Python").check();
  const helpTarget = page.locator("fieldset").filter({ hasText: "Who would you most like to help?" });
  await helpTarget.getByRole("button", { name: "Creators" }).click();
  await page.screenshot({ path: `${captures}/04-direction.png`, fullPage: true });
  await page.getByRole("button", { name: "Build my stack" }).click();

  await expect(page.getByRole("heading", { name: "Your skills" })).toBeVisible({ timeout: 15_000 });
  await page.screenshot({ path: `${captures}/05-overview.png`, fullPage: true });
  const resultNav = page.getByRole("navigation", { name: "Skill Stack results" });
  await resultNav.getByRole("button", { name: "Who you are", exact: true }).click();
  await expect(page.getByText("Professional profile", { exact: true })).toBeVisible();
  await page.screenshot({ path: `${captures}/06-profile.png`, fullPage: true });
  await resultNav.getByRole("button", { name: "Develop", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Build the missing capability" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Resources" })).toBeVisible();
  await page.screenshot({ path: `${captures}/07-develop.png`, fullPage: true });
  await resultNav.getByRole("button", { name: "Skill map", exact: true }).click();
  await expect(page.getByLabel("Skill Stack map summary")).toBeVisible();
  await page.screenshot({ path: `${captures}/08-map.png`, fullPage: true });

  await selectSkill(page, "Spring Boot");
  await selectSkill(page, "Python");
  await page.getByRole("button", { name: "Discover intersections" }).click();
  const discovery = page.getByLabel("Discovered intersection");
  await expect(discovery).toBeVisible({ timeout: 8_000 });
  await page.screenshot({ path: `${captures}/09-discover.png`, fullPage: true });
  await discovery.getByRole("button", { name: "Turn into project" }).click();
  await expect(discovery.getByRole("button", { name: "Project created" })).toBeVisible();
  await page.getByRole("button", { name: /Return to Skill Stack/ }).click();

  await openSearchResult(page, "Spring Boot");
  await expect(page.getByText("proven", { exact: true })).toBeVisible();
  await expect(page.getByText(/Project-backed by/)).toBeVisible();
  await page.getByRole("button", { name: "Close details" }).click();

  const firstToken = await page.evaluate(() => window.localStorage.getItem("skill-stack-access-token"));
  expect(firstToken).toBeTruthy();
  const firstGraph = await page.request.get("http://localhost:8080/api/v1/graph", {
    headers: { Authorization: `Bearer ${firstToken}` },
  });
  expect(firstGraph.ok()).toBe(true);
  const javaId = ((await firstGraph.json()) as { nodes: { id: string; title: string }[] }).nodes.find((node) => node.title === "Spring Boot")?.id;
  expect(javaId).toBeTruthy();

  await signOut(page, "Phase 2.5 Explorer");
  await expect(page.getByRole("button", { name: "Build my Skill Stack" })).toBeVisible();
  await page.getByLabel("How should we address you?").fill("Phase 2.5 Explorer");
  await page.getByRole("button", { name: "Build my Skill Stack" }).click();
  await expect(page.getByRole("navigation", { name: "Skill Stack results" })).toBeVisible();
  await openSearchResult(page, "Spring Boot");
  await expect(page.getByText(/Project-backed by/)).toBeVisible();
  await page.screenshot({ path: `${captures}/10-returning-user.png`, fullPage: true });

  await page.getByRole("button", { name: "Close details" }).click();
  await signOut(page, "Phase 2.5 Explorer");
  await page.evaluate(() => window.localStorage.removeItem("skill-stack-returning-subject"));
  await page.getByLabel("How should we address you?").fill("Ownership Boundary");
  await page.getByRole("button", { name: "Build my Skill Stack" }).click();
  await expect(page.getByRole("heading", { name: /See how your skills combine/ })).toBeVisible();
  const secondToken = await page.evaluate(() => window.localStorage.getItem("skill-stack-access-token"));
  const foreignRead = await page.request.get(`http://localhost:8080/api/v1/nodes/${javaId}`, {
    headers: { Authorization: `Bearer ${secondToken}` },
  });
  expect(foreignRead.status()).toBe(404);
  expect(browserErrors).toEqual([]);
});

test("Phase 2.5 works on mobile and exposes keyboard navigation", async ({ page }) => {
  test.setTimeout(60_000);
  await page.setViewportSize({ width: 390, height: 844 });
  await openFreshLogin(page);
  await page.getByLabel("How should we address you?").fill("Mobile Explorer");
  await page.getByRole("button", { name: "Build my Skill Stack" }).click();
  const onboarding = page.getByRole("dialog");
  await expect(onboarding.getByRole("heading", { name: /See how your skills combine/ })).toBeVisible();
  const bounds = await onboarding.boundingBox();
  expect(bounds).not.toBeNull();
  expect((bounds?.x ?? -1) >= 0 && (bounds?.x ?? 390) + (bounds?.width ?? 390) <= 390).toBe(true);
  await page.screenshot({ path: `${captures}/11-mobile.png`, fullPage: true });

  await openFreshLogin(page);
  await page.getByRole("button", { name: "Explore the existing demo data" }).click();
  await page.getByRole("navigation", { name: "Skill Stack results" }).getByRole("button", { name: "Skill map", exact: true }).click();
  const canvas = page.getByRole("application", { name: /Interactive Skill Stack map/ });
  await expect(canvas).toBeVisible();
  await canvas.focus();
  await canvas.press("Home");
  const announcement = page.locator('.sr-only[role="status"]');
  await expect(announcement).not.toHaveText("");
  const firstAnnouncement = await announcement.textContent();
  await canvas.press("ArrowRight");
  await expect(announcement).not.toHaveText(firstAnnouncement ?? "");
  await canvas.press("Enter");
  await expect(page.locator("aside.node-sheet")).toBeVisible();
});

test("reduced motion completes database-backed discovery without decorative delay", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await openFreshLogin(page);
  await page.getByRole("button", { name: "Explore the existing demo data" }).click();
  await page.getByRole("navigation", { name: "Skill Stack results" }).getByRole("button", { name: "Skill map", exact: true }).click();
  await selectSkill(page, "Backend development with Spring Boot");
  await selectSkill(page, "Spring Boot");
  await page.getByRole("button", { name: "Discover intersections" }).click();
  await expect(page.getByLabel("Discovered intersection")).toBeVisible({ timeout: 3_000 });
});

async function signOut(page: Page, displayName: string) {
  await page.getByRole("button", { name: `Open ${displayName}'s private account controls` }).click();
  const account = page.getByRole("dialog", { name: "Your account" });
  await expect(account).toBeVisible();
  await account.getByRole("button", { name: "Sign out" }).click();
}

async function openFreshLogin(page: Page) {
  await page.route("**/api/v1/intelligence/recommendations/latest", async (route) => {
    await route.fulfill({ status: 204 });
  });
  await page.route("**/api/v1/intelligence/status", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      enabled: false, provider: "disabled", model: "none", monthlyBudgetUsd: 0, totalBudgetUsd: 0,
      maxCallsPerRequest: 0, maxInputCharacters: 0, maxCallsPerUserMonth: 0, maxEstimatedCostPerCallUsd: 0,
      state: "AI_DISABLED",
    }) });
  });
  await page.route("**/api/v1/intelligence/recommendations", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: "null" });
  });
  await page.goto("/");
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();
  await expect(page.getByRole("heading", { name: "Know the next skill worth building." })).toBeVisible();
}

async function openSearchResult(page: Page, name: string) {
  const search = page.getByRole("textbox", { name: "Search your Skill Stack" });
  await search.fill(name);
  await page.getByRole("option", { name: new RegExp(`^${name} SKILL`) }).click();
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();
}

async function selectSkill(page: Page, name: string) {
  await openSearchResult(page, name);
  await page.getByRole("button", { name: "Add to intersection" }).click();
  await page.getByRole("button", { name: "Close details" }).click();
}
