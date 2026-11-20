import { expect, test } from "@playwright/test";

test("Sigma remains interactive across performance scales", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  for (const count of [100, 500, 1000, 5000]) {
    await page.goto(`/experiments?engine=sigma&count=${count}`);
    await expect(page.getByRole("heading", { name: `sigma / ${count} nodes` })).toBeVisible();
    const canvas = page.locator(".graph-canvas canvas.sigma-mouse");
    await expect(canvas).toBeVisible({ timeout: 10_000 });
    await canvas.hover();
    await page.mouse.wheel(0, -360);
    await page.mouse.wheel(0, 360);
  }
  expect(errors).toEqual([]);
});

for (const engine of ["cytoscape", "reactflow"] as const) {
  test(`${engine} comparison route renders the shared 500-node dataset`, async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto(`/experiments?engine=${engine}&count=500`);
    await expect(page.getByRole("heading", { name: `${engine} / 500 nodes` })).toBeVisible();
    await expect(page.locator(".experiment-renderer")).toBeVisible();
    await page.locator(".experiment-renderer").hover();
    await page.mouse.wheel(0, -300);
    expect(errors).toEqual([]);
  });
}
