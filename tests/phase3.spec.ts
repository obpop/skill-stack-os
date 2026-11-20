import { expect, test, type Page } from "@playwright/test";
import type { IntelligenceRecommendation } from "../lib/api/types";

const captures = "public/captures/phase-3-ui-redesign";
const deterministicRecommendation = {
  recommendationId: "00000000-0000-0000-0000-000000000001",
  generatedAt: "2026-10-17T00:00:00Z",
  inputVersion: "ui-redesign-fixture",
  engine: "DETERMINISTIC",
  recommendedStrategyId: "specialist-amplifier",
  headline: "Deepen backend development through a focused systems project",
  summary: "Backend development and systems work provide a practical base for a small project with inspectable results.",
  contributingSkillIds: ["skill-spring-boot-backend", "skill-backend-development"],
  strategies: [
    {
      id: "specialist-amplifier", strategy: "SPECIALIST_AMPLIFIER", title: "Deepen a specialty and add an amplifier", recommended: true,
      summary: "Use systems thinking to make one backend capability easier to explain and verify.",
      contributions: [
        { skillId: "skill-spring-boot-backend", title: "Backend development with Spring Boot", contribution: "Provides an implementation base.", evidence: "Owned skill" },
        { skillId: "skill-backend-development", title: "Backend development", contribution: "Provides a practical system context.", evidence: "Owned skill" },
      ],
      synergy: "A focused implementation can connect technical depth with clearer system decisions.",
      plausibleApplications: ["A backend reliability improvement", "A documented architecture decision"],
      limitations: ["Usefulness still needs evidence"], missingCapability: "System design", tradeoff: "Depth adds less breadth.",
      firstAction: "Choose one service boundary and document the tradeoff.", evidence: [],
    },
    {
      id: "technical-commercial", strategy: "TECHNICAL_COMMERCIAL", title: "Connect technical work to a practical test", recommended: false,
      summary: "Apply an existing backend skill to one narrow user or business problem.",
      contributions: [{ skillId: "skill-backend-development", title: "Backend development", contribution: "Supports a bounded delivery test.", evidence: "Owned skill" }],
      synergy: "A small delivery test can show whether the capability changes an outcome.",
      plausibleApplications: ["An internal workflow tool", "A small service prototype"],
      limitations: ["Demand is unverified"], missingCapability: "User research", tradeoff: "Feedback arrives faster, but technical depth may grow more slowly.",
      firstAction: "Ask one user to describe a repeated workflow problem.", evidence: [],
    },
  ],
  nextSkill: {
    title: "System design", ownedSkillId: null,
    whyNow: "It can turn existing backend work into clearer architectural decisions.",
    alternatives: [{ title: "Observability", tradeoff: "It creates faster operational evidence.", uncertainty: "Medium uncertainty" }],
    levers: ["Reliability", "Technical communication"],
    firstAction: "Document one service boundary.", evidenceOutcome: "A short decision record with an observable result.",
  },
  development: { educationItemIds: [], networkItemIds: [], moves: [], buildProjectSuggestion: "Build one bounded backend case study.", firstAction: "Define the result before implementation." },
  why: { evidence: [], profileFieldsUsed: [], assumptions: ["A small project is feasible."], tradeoffs: ["Depth adds less breadth."], resourceVerification: "Verified catalog only.", retrievalReasons: [] },
  userDecision: "UNREVIEWED", selectedStrategyId: null,
  provenance: { schemaVersion: "phase3-v1", provider: "deterministic", model: "none", operationId: null, cacheHit: true, boundedContextCharacters: 0, sourceCount: 0, costState: "AI_DISABLED" },
} satisfies IntelligenceRecommendation;

test.describe.configure({ mode: "serial" });

test("the five compact application pages preserve the canonical recommendation", async ({ page }) => {
  test.setTimeout(60_000);
  const browserErrors: string[] = [];
  page.on("console", (message) => { if (message.type() === "error") browserErrors.push(message.text()); });
  page.on("pageerror", (error) => browserErrors.push(error.message));

  await openDemo(page);
  const nav = page.getByRole("navigation", { name: "Skill Stack results" });
  await expect(nav.getByRole("button")).toHaveCount(5);
  await expect(nav.getByRole("button", { name: "Skills", exact: true })).toBeVisible();
  await expect(nav.getByRole("button", { name: "Paths", exact: true })).toBeVisible();
  await expect(nav.getByRole("button", { name: "Who you are", exact: true })).toBeVisible();
  await expect(nav.getByRole("button", { name: "Skill map", exact: true })).toBeVisible();
  await expect(nav.getByRole("button", { name: "Develop", exact: true })).toBeVisible();

  await nav.getByRole("button", { name: "Skills", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Your skills" })).toBeVisible();
  await expect(page.getByLabel("Add a skill")).toBeVisible();
  await expect(page.locator(".skill-chip").first()).toBeVisible();
  await page.screenshot({ path: `${captures}/01-skills.png`, fullPage: true });

  await nav.getByRole("button", { name: "Paths", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Paths for you" })).toBeVisible();
  expect(await page.locator(".path-card").count()).toBeGreaterThanOrEqual(2);
  await expect(page.getByLabel("Selected path details")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Individual skills to explore" })).toBeVisible();
  const pathHeadline = (await page.locator(".path-card.active h2").textContent())?.trim() ?? "";
  expect(pathHeadline).not.toBe("");
  await page.screenshot({ path: `${captures}/02-paths.png`, fullPage: true });

  await nav.getByRole("button", { name: "Who you are", exact: true }).click();
  await expect(page.getByText("Professional profile", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Key strengths" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Skill patterns" })).toBeVisible();
  await page.screenshot({ path: `${captures}/03-profile.png`, fullPage: true });

  await nav.getByRole("button", { name: "Skill map", exact: true }).click();
  await expect(page.getByLabel("Skill map view")).toBeVisible();
  await expect(page.getByRole("application", { name: /Interactive Skill Stack map/ })).toBeVisible();
  await page.getByLabel("Skill map view").getByRole("button", { name: "List" }).click();
  await expect(page.getByRole("dialog", { name: "Structured graph directory" })).toBeVisible();
  await page.getByRole("dialog", { name: "Structured graph directory" }).getByLabel("Close").click();
  await page.screenshot({ path: `${captures}/04-map.png`, fullPage: true });

  await nav.getByRole("button", { name: "Develop", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Ways to develop" })).toBeVisible();
  await expect(page.getByText("Current selected direction", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Build the missing capability" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Resources" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Practical next steps" })).toBeVisible();
  await page.screenshot({ path: `${captures}/05-develop.png`, fullPage: true });

  await page.getByRole("button", { name: /Open .* private account controls/ }).click();
  const account = page.getByRole("dialog", { name: "Your account" });
  await expect(account.getByText("Private by default")).toBeVisible();
  await expect(account.getByRole("button", { name: "Download my data" })).toBeVisible();
  await expect(account.getByRole("button", { name: "Delete account" })).toBeVisible();
  await page.screenshot({ path: "public/captures/phase-5/01-account-privacy.png", fullPage: true });
  await account.getByRole("button", { name: "Close account controls" }).click();

  expect(browserErrors).toEqual([]);
});

test("the redesigned application fits tablet and mobile viewports", async ({ page }) => {
  await page.setViewportSize({ width: 834, height: 1112 });
  await openDemo(page);
  await expect(page.locator(".path-card").first()).toBeVisible({ timeout: 15_000 });
  await expectNoHorizontalOverflow(page);
  await page.screenshot({ path: `${captures}/06-tablet-paths.png`, fullPage: true });

  await page.setViewportSize({ width: 390, height: 844 });
  const nav = page.getByRole("navigation", { name: "Skill Stack results" });
  await nav.getByRole("button", { name: "Skills", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Your skills" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await nav.getByRole("button", { name: "Paths", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Paths for you" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await nav.getByRole("button", { name: "Develop", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Ways to develop" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await page.screenshot({ path: `${captures}/07-mobile-develop.png`, fullPage: true });
});

test("durable analysis resumes after reload and publishes the completed result", async ({ page }) => {
  let starts = 0;
  let reads = 0;
  await page.route("**/api/v1/intelligence/status", (route) => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
    enabled: true, provider: "openai", model: "gpt-6-sol", monthlyBudgetUsd: 5, totalBudgetUsd: 5,
    maxCallsPerRequest: 1, maxInputCharacters: 12_000, maxCallsPerUserMonth: 20, maxEstimatedCostPerCallUsd: 0.05,
    state: "BOUNDED_PROVIDER_CALL",
  }) }));
  await page.route("**/api/v1/intelligence/recommendations/latest", (route) => route.fulfill({
    status: 200, contentType: "application/json", body: JSON.stringify(deterministicRecommendation),
  }));
  await page.route("**/api/v1/operations/recommendations", (route) => {
    starts += 1;
    return route.fulfill({ status: 202, contentType: "application/json", body: JSON.stringify(jobFixture("QUEUED", "READING_STACK")) });
  });
  await page.route("**/api/v1/operations/jobs/job-1", (route) => {
    reads += 1;
    const complete = reads > 1;
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(jobFixture(
      complete ? "SUCCEEDED" : "RUNNING", complete ? "COMPLETED" : "COMPARING_COMBINATIONS", complete ? deterministicRecommendation.recommendationId : null,
    )) });
  });
  await page.route(`**/api/v1/intelligence/recommendations/${deterministicRecommendation.recommendationId}`, (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(deterministicRecommendation) }));

  await page.goto("/");
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();
  await page.getByRole("button", { name: "Explore the existing demo data" }).click();
  await expect(page.getByText("Reading your stack")).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole("heading", { name: "Deepen a specialty and add an amplifier" }).first()).toBeVisible();
  await page.screenshot({ path: "public/captures/phase-4/01-analysis-queued.png", fullPage: true });
  await page.reload();
  await expect(page.getByText("Comparing combinations")).toBeVisible({ timeout: 15_000 });
  await page.screenshot({ path: "public/captures/phase-4/02-analysis-resumed.png", fullPage: true });
  await expect(page.getByRole("heading", { name: "Deepen a specialty and add an amplifier" }).first()).toBeVisible({ timeout: 5_000 });
  await expect(page.locator(".analysis-progress")).toHaveCount(0);
  expect(starts).toBe(1);
});

function jobFixture(status: string, stage: string, resultId: string | null = null) {
  return {
    id: "job-1", operationType: "RECOMMENDATION", status, stage, attemptCount: 0, maxAttempts: 2,
    resultId, safeErrorCode: null, cancellationRequested: false,
    availableAt: "2026-10-17T00:00:00Z", createdAt: "2026-10-17T00:00:00Z",
    updatedAt: "2026-10-17T00:00:00Z", completedAt: status === "SUCCEEDED" ? "2026-10-17T00:00:02Z" : null,
  };
}

async function openDemo(page: Page) {
  await page.route("**/api/v1/intelligence/recommendations/latest", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(deterministicRecommendation) });
  });
  await page.route("**/api/v1/intelligence/status", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      enabled: false, provider: "disabled", model: "none", monthlyBudgetUsd: 0, totalBudgetUsd: 0,
      maxCallsPerRequest: 0, maxInputCharacters: 0, maxCallsPerUserMonth: 0, maxEstimatedCostPerCallUsd: 0,
      state: "AI_DISABLED",
    }) });
  });
  await page.route("**/api/v1/intelligence/recommendations", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(deterministicRecommendation) });
  });
  await page.goto("/");
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();
  await page.getByRole("button", { name: "Explore the existing demo data" }).click();
  await expect(page.getByRole("navigation", { name: "Skill Stack results" })).toBeVisible({ timeout: 15_000 });
}

async function expectNoHorizontalOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
}
