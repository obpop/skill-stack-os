import type { AiStatus, ApiGraph, ApiNode, ApiProblem, ApiProfile, ApiSearch, Auth0Logout, Auth0Session, BackgroundJob, DevelopResult, DevSession, ImportJob, IntelligenceDecision, IntelligenceRecommendation, PublicationOwner, PublicationShareCreated, StackDiscovery, StackSummary } from "@/lib/api/types";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1";
const REQUEST_TIMEOUT_MS = 12_000;

export const authConfig = {
  mode: process.env.NEXT_PUBLIC_AUTH_MODE === "auth0" ? "auth0" as const : "local" as const,
  loginUrl: process.env.NEXT_PUBLIC_AUTH_LOGIN_URL ?? "http://localhost:8080/oauth2/authorization/auth0",
};

export class ApiClientError extends Error {
  constructor(message: string, public readonly status: number, public readonly problem?: ApiProblem) {
    super(message);
  }
}

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  let response: Response;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        ...(options.body && !(options.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    });
  } catch (problem) {
    if (controller.signal.aborted) {
      const message = "The request timed out. Your saved universe was not changed; retry when the connection is stable.";
      throw new ApiClientError(message, 408, { status: 408, code: "REQUEST_TIMEOUT", message });
    }
    throw new ApiClientError("The Skill Stack API is not reachable. Start PostgreSQL and the Spring Boot backend, then retry.", 0);
  } finally {
    clearTimeout(timeout);
  }

  if (!response.ok) {
    const problem = await response.json().catch(() => undefined) as ApiProblem | undefined;
    throw new ApiClientError(problem?.message ?? `Request failed (${response.status})`, response.status, problem);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  login: (body: { displayName?: string; returningSubject?: string; demo: boolean }) =>
    request<DevSession>("/auth/dev-login", { method: "POST", body: JSON.stringify(body) }),
  authSession: () => request<Auth0Session>("/auth/session", { credentials: "include" }),
  authLogout: (csrfHeaderName: string, csrfToken: string) => request<Auth0Logout>("/auth/session/logout", {
    method: "POST",
    credentials: "include",
    headers: { [csrfHeaderName]: csrfToken },
  }),
  graph: (token: string) => request<ApiGraph>("/graph", {}, token),
  profile: (token: string) => request<ApiProfile>("/profile", {}, token),
  updateProfile: (token: string, body: object) => request<ApiProfile>("/profile", { method: "PUT", body: JSON.stringify(body) }, token),
  stackSummary: (token: string) => request<StackSummary>("/stack/summary", {}, token),
  develop: (token: string) => request<DevelopResult>("/develop", {}, token),
  intelligence: (token: string, skillIds: string[] = [], refresh = false) => request<IntelligenceRecommendation>("/intelligence/recommendations", { method: "POST", body: JSON.stringify({ skillIds, refresh }) }, token),
  latestIntelligence: (token: string) => request<IntelligenceRecommendation | undefined>("/intelligence/recommendations/latest", {}, token),
  intelligenceById: (token: string, id: string) => request<IntelligenceRecommendation>(`/intelligence/recommendations/${id}`, {}, token),
  aiStatus: (token: string) => request<AiStatus>("/intelligence/status", {}, token),
  startRecommendationJob: (token: string, skillIds: string[] = [], refresh = false, requestKey?: string) => request<BackgroundJob>("/operations/recommendations", { method: "POST", body: JSON.stringify({ skillIds, refresh, requestKey: requestKey ?? null }) }, token),
  backgroundJob: (token: string, id: string) => request<BackgroundJob>(`/operations/jobs/${id}`, {}, token),
  cancelBackgroundJob: (token: string, id: string) => request<BackgroundJob>(`/operations/jobs/${id}/cancel`, { method: "POST" }, token),
  retryBackgroundJob: (token: string, id: string) => request<BackgroundJob>(`/operations/jobs/${id}/retry`, { method: "POST" }, token),
  decideIntelligence: (token: string, id: string, decision: IntelligenceDecision, selectedStrategyId?: string | null) => request<IntelligenceRecommendation>(`/intelligence/recommendations/${id}/decision`, { method: "PATCH", body: JSON.stringify({ decision, selectedStrategyId: selectedStrategyId ?? null }) }, token),
  discover: (token: string, skillIds: string[], interestIds: string[] = []) => request<StackDiscovery>("/intersections/discover", { method: "POST", body: JSON.stringify({ skillIds, interestIds }) }, token),
  createImport: (token: string, file: File) => { const body = new FormData(); body.append("file", file); return request<ImportJob>("/imports", { method: "POST", body }, token); },
  importJob: (token: string, id: string) => request<ImportJob>(`/imports/${id}`, {}, token),
  acceptImport: (token: string, id: string, items: object[]) => request(`/imports/${id}/accept`, { method: "POST", body: JSON.stringify({ items }) }, token),
  completeOnboarding: (token: string) => request("/auth/onboarding/complete", { method: "POST" }, token),
  updatePreferences: (token: string, body: { defaultDomain: string }) => request("/auth/preferences", { method: "PATCH", body: JSON.stringify(body) }, token),
  exportAccount: (token: string) => request<Record<string, unknown>>("/auth/account/export", {}, token),
  deleteAccount: (token: string) => request<void>("/auth/account", { method: "DELETE" }, token),
  publication: (token: string) => request<PublicationOwner>("/publication", {}, token),
  updatePublication: (token: string, body: object) => request<PublicationOwner>("/publication", { method: "PUT", body: JSON.stringify(body) }, token),
  publishProfile: (token: string) => request<PublicationOwner>("/publication/publish", { method: "POST" }, token),
  unpublishProfile: (token: string) => request<PublicationOwner>("/publication/unpublish", { method: "POST" }, token),
  createPublicShare: (token: string, label?: string) => request<PublicationShareCreated>("/publication/shares", { method: "POST", body: JSON.stringify({ label: label || null }) }, token),
  revokePublicShare: (token: string, id: string) => request<void>(`/publication/shares/${id}`, { method: "DELETE" }, token),
  addTimelineMilestone: (token: string, body: object) => request<PublicationOwner>("/publication/timeline", { method: "POST", body: JSON.stringify(body) }, token),
  deleteTimelineMilestone: (token: string, id: string) => request<PublicationOwner>(`/publication/timeline/${id}`, { method: "DELETE" }, token),
  search: (token: string, query: string) => request<ApiSearch>(`/search?q=${encodeURIComponent(query)}`, {}, token),
  createNode: (token: string, body: object) => request<ApiNode>("/nodes", { method: "POST", body: JSON.stringify(body) }, token),
  updateNode: (token: string, id: string, body: object) => request<ApiNode>(`/nodes/${id}`, { method: "PATCH", body: JSON.stringify(body) }, token),
  deleteNode: (token: string, id: string) => request<void>(`/nodes/${id}`, { method: "DELETE" }, token),
  createEdge: (token: string, body: object) => request("/edges", { method: "POST", body: JSON.stringify(body) }, token),
  updateEdge: (token: string, id: string, body: object) => request(`/edges/${id}`, { method: "PATCH", body: JSON.stringify(body) }, token),
  deleteEdge: (token: string, id: string) => request<void>(`/edges/${id}`, { method: "DELETE" }, token),
  createProject: (token: string, body: object) => request("/projects", { method: "POST", body: JSON.stringify(body) }, token),
  updateProject: (token: string, id: string, body: object) => request(`/projects/${id}`, { method: "PATCH", body: JSON.stringify(body) }, token),
  deleteProject: (token: string, id: string) => request<void>(`/projects/${id}`, { method: "DELETE" }, token),
  addProof: (token: string, projectId: string, body: object) => request(`/projects/${projectId}/proofs`, { method: "POST", body: JSON.stringify(body) }, token),
  deleteProof: (token: string, proofId: string) => request<void>(`/projects/proofs/${proofId}`, { method: "DELETE" }, token),
  decideIntersection: (token: string, key: string, body: object) => request<void>(`/intersections/${encodeURIComponent(key)}`, { method: "PUT", body: JSON.stringify(body) }, token),
  convertIntersection: (token: string, key: string, body: object) => request(`/intersections/${encodeURIComponent(key)}/project`, { method: "POST", body: JSON.stringify(body) }, token),
};
