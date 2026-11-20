import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("API client failure behavior", () => {
  it("turns a stalled request into an explicit timeout without waiting forever", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn((_input: RequestInfo | URL, init?: RequestInit) => new Promise<Response>((_resolve, reject) => {
      init?.signal?.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")));
    })));

    const expectation = expect(api.graph("test-token")).rejects.toMatchObject({
      status: 408,
      problem: { code: "REQUEST_TIMEOUT" },
    });
    await vi.advanceTimersByTimeAsync(12_000);
    await expectation;
  });

  it("preserves structured backend errors for contextual mutation feedback", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({
      status: 409,
      code: "STALE_UPDATE",
      message: "The node changed in another tab",
      fieldErrors: { version: "Reload and retry" },
    }), { status: 409, headers: { "Content-Type": "application/json" } })));

    await expect(api.updateNode("test-token", "node-id", {})).rejects.toEqual(expect.objectContaining({
      status: 409,
      message: "The node changed in another tab",
      problem: expect.objectContaining({ code: "STALE_UPDATE", fieldErrors: { version: "Reload and retry" } }),
    }));
  });
});

describe("Auth0 session bridge", () => {
  it("restores the Spring login session with credentials", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      accessToken: "access-token",
      expiresAt: "2026-09-27T12:00:00Z",
      csrfHeaderName: "X-CSRF-TOKEN",
      csrfToken: "csrf-token",
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(api.authSession()).resolves.toMatchObject({ accessToken: "access-token" });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/auth/session",
      expect.objectContaining({ credentials: "include" }),
    );
  });

  it("sends the issued CSRF token when ending the Spring login session", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      logoutUrl: "https://tenant.example/v2/logout",
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await api.authLogout("X-CSRF-TOKEN", "csrf-token");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/auth/session/logout",
      expect.objectContaining({
        method: "POST",
        credentials: "include",
        headers: expect.objectContaining({ "X-CSRF-TOKEN": "csrf-token" }),
      }),
    );
  });
});

describe("Phase 3 canonical intelligence contract", () => {
  it("sends one bounded skill selection to the canonical recommendation endpoint", async () => {
    const recommendation = { recommendationId: "recommendation-id", strategies: [], contributingSkillIds: ["skill-a", "skill-b"] };
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(recommendation), {
      status: 200, headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(api.intelligence("test-token", ["skill-a", "skill-b"])).resolves.toMatchObject(recommendation);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/intelligence/recommendations",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ skillIds: ["skill-a", "skill-b"], refresh: false }),
        headers: expect.objectContaining({ Authorization: "Bearer test-token" }),
      }),
    );
  });

  it("persists a selected alternative through the recommendation lifecycle", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      recommendationId: "recommendation-id", userDecision: "MODIFIED", selectedStrategyId: "bridge_domains",
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await api.decideIntelligence("test-token", "recommendation-id", "MODIFIED", "bridge_domains");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/intelligence/recommendations/recommendation-id/decision",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ decision: "MODIFIED", selectedStrategyId: "bridge_domains" }),
      }),
    );
  });
});

describe("Phase 4 durable analysis contract", () => {
  it("starts, reads, cancels and retries an owner-scoped background job", async () => {
    const job = { id: "job-id", status: "QUEUED", stage: "READING_STACK" };
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(job), {
      status: 200, headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await api.startRecommendationJob("test-token", ["skill-a"], true, "request-1");
    await api.backgroundJob("test-token", "job-id");
    await api.cancelBackgroundJob("test-token", "job-id");
    await api.retryBackgroundJob("test-token", "job-id");

    expect(fetchMock).toHaveBeenNthCalledWith(1,
      "http://localhost:8080/api/v1/operations/recommendations",
      expect.objectContaining({ method: "POST", body: JSON.stringify({ skillIds: ["skill-a"], refresh: true, requestKey: "request-1" }) }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(2, "http://localhost:8080/api/v1/operations/jobs/job-id", expect.anything());
    expect(fetchMock).toHaveBeenNthCalledWith(3, "http://localhost:8080/api/v1/operations/jobs/job-id/cancel", expect.objectContaining({ method: "POST" }));
    expect(fetchMock).toHaveBeenNthCalledWith(4, "http://localhost:8080/api/v1/operations/jobs/job-id/retry", expect.objectContaining({ method: "POST" }));
  });
});
