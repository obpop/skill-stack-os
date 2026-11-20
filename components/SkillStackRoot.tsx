"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { SkillStackApp } from "@/components/SkillStackApp";
import { PublicLanding } from "@/components/PublicLanding";
import type { ImportReviewItem, OnboardingDraft } from "@/components/OnboardingGuide";
import { universe } from "@/data/universe";
import { apiNodeToUniverse, graphToUniverse } from "@/lib/api/adapter";
import { api, ApiClientError, authConfig } from "@/lib/api/client";
import type { ApiGraph, Auth0Session, BackgroundJob, DevelopResult, ImportJob, IntelligenceDecision, IntelligenceRecommendation, PublicationConfig, PublicationOwner, PublicationShareCreated, StackSummary, TimelineEventType } from "@/lib/api/types";
import type { DomainId, IntersectionDefinition, ProjectDefinition, ProofDefinition, UniverseEdge, UniverseNode } from "@/lib/types";
import type { NodeEditValue, ProjectEditValue, RelationshipEditValue } from "@/components/NodeSheet";
import type { ComposerValue } from "@/components/UniverseComposer";
import { Logo } from "@/components/site/Logo";

const TOKEN_KEY = "skill-stack-access-token";
const SUBJECT_KEY = "skill-stack-returning-subject";
const ACTIVE_ANALYSIS_KEY = "skill-stack-active-analysis";
const AUTH0_MODE = authConfig.mode === "auth0";
const TERMINAL_JOB_STATES = new Set(["SUCCEEDED", "FAILED", "CANCELLED"]);

export function SkillStackRoot() {
  const [ready, setReady] = useState(false);
  const [phaseOneMode, setPhaseOneMode] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [auth0Session, setAuth0Session] = useState<Auth0Session | null>(null);
  const [graph, setGraph] = useState<ApiGraph | null>(null);
  const [stackSummary, setStackSummary] = useState<StackSummary | null>(null);
  const [develop, setDevelop] = useState<DevelopResult | null>(null);
  const [intelligence, setIntelligence] = useState<IntelligenceRecommendation | null>(null);
  const [analysisJob, setAnalysisJob] = useState<BackgroundJob | null>(null);
  const [analysisError, setAnalysisError] = useState<string | null>(null);
  const [publication, setPublication] = useState<PublicationOwner | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const phaseOne = params.get("mode") === "phase1";
    setPhaseOneMode(phaseOne);
    if (phaseOne) {
      setReady(true);
      return;
    }
    if (!AUTH0_MODE) {
      setToken(window.localStorage.getItem(TOKEN_KEY));
      setReady(true);
      return;
    }
    if (params.get("auth") === "error") setError("Auth0 could not complete the sign-in. Please try again.");
    void api.authSession()
      .then((session) => {
        setAuth0Session(session);
        setToken(session.accessToken);
      })
      .catch((problem) => {
        if (!(problem instanceof ApiClientError && problem.status === 401)) {
          setError(problem instanceof Error ? problem.message : "Unable to restore the Auth0 session.");
        }
      })
      .finally(() => setReady(true));
  }, []);

  const clearAuthentication = useCallback(() => {
    if (!AUTH0_MODE) window.localStorage.removeItem(TOKEN_KEY);
    setAuth0Session(null);
    setGraph(null);
    setIntelligence(null);
    setPublication(null);
    window.localStorage.removeItem(ACTIVE_ANALYSIS_KEY);
    setAnalysisJob(null);
    setToken(null);
  }, []);

  const pollAnalysis = useCallback(async (activeToken: string, initial: BackgroundJob) => {
    let current = initial;
    setAnalysisJob(current);
    window.localStorage.setItem(ACTIVE_ANALYSIS_KEY, current.id);
    for (let attempt = 0; attempt < 120 && !TERMINAL_JOB_STATES.has(current.status); attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000));
      current = await api.backgroundJob(activeToken, current.id);
      setAnalysisJob(current);
    }
    if (!TERMINAL_JOB_STATES.has(current.status)) return;
    window.localStorage.removeItem(ACTIVE_ANALYSIS_KEY);
    if (current.status === "SUCCEEDED" && current.resultId) {
      setIntelligence(await api.intelligenceById(activeToken, current.resultId));
      setAnalysisError(null);
    } else if (current.status === "FAILED") {
      setAnalysisError(current.safeErrorCode === "INPUT_CHANGED"
        ? "Your skills changed during analysis. Start a new analysis when you are ready."
        : "The analysis could not finish. Your last saved recommendation is still available.");
    }
  }, []);

  const requestIntelligence = useCallback(async (activeToken: string, skillIds: string[] = [], refresh = false) => {
    setAnalysisError(null);
    const status = await api.aiStatus(activeToken);
    if (status.state !== "BOUNDED_PROVIDER_CALL") {
      setIntelligence(await api.intelligence(activeToken, skillIds, refresh));
      setAnalysisJob(null);
      return;
    }
    const requestKey = refresh ? crypto.randomUUID() : undefined;
    const job = await api.startRecommendationJob(activeToken, skillIds, refresh, requestKey);
    await pollAnalysis(activeToken, job);
  }, [pollAnalysis]);

  const load = useCallback(async (activeToken: string) => {
    setLoading(true);
    setError(null);
    try {
      const loadedGraph = await api.graph(activeToken);
      setGraph(loadedGraph);
      if (loadedGraph.user.onboardingComplete) {
        const [summaryResult, developResult, latestRecommendation] = await Promise.all([
          api.stackSummary(activeToken), api.develop(activeToken), api.latestIntelligence(activeToken).catch(() => undefined),
        ]);
        setStackSummary(summaryResult);
        setDevelop(developResult);
        if (latestRecommendation) setIntelligence(latestRecommendation);
        setLoading(false);
        const activeJobId = window.localStorage.getItem(ACTIVE_ANALYSIS_KEY);
        try {
          if (activeJobId) await pollAnalysis(activeToken, await api.backgroundJob(activeToken, activeJobId));
          else await requestIntelligence(activeToken);
        } catch (problem) {
          window.localStorage.removeItem(ACTIVE_ANALYSIS_KEY);
          setAnalysisError(problem instanceof Error ? problem.message : "Analysis is temporarily unavailable.");
        }
      } else {
        setStackSummary(null);
        setDevelop(null);
        setIntelligence(null);
      }
    } catch (problem) {
      if (problem instanceof ApiClientError && problem.status === 401) {
        clearAuthentication();
      }
      setError(problem instanceof Error ? problem.message : "Unable to load your Skill Stack.");
    } finally {
      setLoading(false);
    }
  }, [clearAuthentication, pollAnalysis, requestIntelligence]);

  useEffect(() => {
    if (ready && token && !phaseOneMode) void load(token);
  }, [load, phaseOneMode, ready, token]);

  const mutate = useCallback(async (operation: (activeToken: string) => Promise<unknown>) => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      await operation(token);
      await load(token);
    } catch (problem) {
      if (problem instanceof ApiClientError && problem.status === 401) {
        clearAuthentication();
      }
      setError(problem instanceof Error ? problem.message : "The change could not be saved.");
      setLoading(false);
      throw problem;
    }
  }, [clearAuthentication, load, token]);

  const data = useMemo(() => graph ? graphToUniverse(graph) : null, [graph]);
  const completeSkillStack = useCallback(async (draft: OnboardingDraft) => {
    if (!token || !graph) return;
    setLoading(true); setError(null);
    try {
      const canonical = (value: string) => value.trim().toLowerCase().replaceAll(/[^a-z0-9]+/g, " ").trim();
      const current = await api.graph(token);
      const persistedNodes = [...current.nodes];
      for (const skill of draft.skills) {
        if (persistedNodes.some((node) => node.type === "SKILL" && canonical(node.title) === canonical(skill.title))) continue;
        persistedNodes.push(await api.createNode(token, { type: "SKILL", title: skill.title, description: null, domain: "ideas", importance: skill.importance, depth: 3, status: "EXPLORING", level: skill.level, monetizable: skill.monetizable, learnOrOutsource: null, wouldDoForFree: false }));
      }
      for (const interest of draft.interests) {
        if (persistedNodes.some((node) => node.type === "INTEREST" && canonical(node.title) === canonical(interest.title))) continue;
        persistedNodes.push(await api.createNode(token, { type: "INTEREST", title: interest.title, description: null, domain: "ideas", importance: 4, depth: 2, status: "CURIOUS", level: null, monetizable: false, learnOrOutsource: null, wouldDoForFree: interest.wouldDoForFree }));
      }
      const allSkills = persistedNodes.filter((node) => node.type === "SKILL");
      const focusSkillIds = draft.focusSkillTitles.map((title) => allSkills.find((node) => node.title.localeCompare(title, undefined, { sensitivity: "base" }) === 0)?.id).filter(Boolean) as string[];
      await api.updateProfile(token, { displayName: draft.displayName, city: draft.city || null, country: draft.country || null, currentRole: draft.currentRole, targetRole: draft.buildOnTheSide ? draft.currentRole : draft.targetRole, buildOnTheSide: draft.buildOnTheSide, timelineYears: draft.timelineYears, skillDirection: draft.skillDirection, focusSkillIds, workPreference: draft.workPreference, helpTarget: draft.helpTarget, complete: true });
      await load(token);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Your stack could not be saved.");
      setLoading(false);
      throw problem;
    }
  }, [graph, load, token]);

  const createImport = useCallback(async (file: File) => {
    if (!token) throw new Error("Authentication is required.");
    setLoading(true); setError(null);
    try { return await api.createImport(token, file); }
    catch (problem) { setError(problem instanceof Error ? problem.message : "The text could not be reviewed."); throw problem; }
    finally { setLoading(false); }
  }, [token]);

  const acceptImport = useCallback(async (job: ImportJob, items: ImportReviewItem[]) => {
    if (!token) return;
    setLoading(true); setError(null);
    try {
      await api.acceptImport(token, job.id, items.filter((item) => item.selected).map((item) => ({ id: item.id, title: item.title, type: item.type, level: item.level, monetizable: false, wouldDoForFree: false })));
      await load(token);
    } catch (problem) { setError(problem instanceof Error ? problem.message : "The reviewed items could not be added."); setLoading(false); throw problem; }
  }, [load, token]);

  const discoverSkills = useCallback(async (selected: UniverseNode[]) => {
    if (!token) throw new Error("Authentication is required.");
    setLoading(true); setError(null);
    try {
      const skillIds = selected.filter((node) => node.type === "SKILL").map((node) => node.id);
      const discovery = await api.discover(token, selected.map((node) => node.id));
      if (skillIds.length) void requestIntelligence(token, skillIds).catch((problem) => {
        setAnalysisError(problem instanceof Error ? problem.message : "Analysis is temporarily unavailable.");
      });
      return discovery;
    }
    catch (problem) { setError(problem instanceof Error ? problem.message : "The intersection could not be calculated."); throw problem; }
    finally { setLoading(false); }
  }, [requestIntelligence, token]);
  const decideIntelligence = useCallback(async (decision: IntelligenceDecision, selectedStrategyId?: string | null) => {
    if (!token || !intelligence) return;
    setLoading(true); setError(null);
    try { setIntelligence(await api.decideIntelligence(token, intelligence.recommendationId, decision, selectedStrategyId)); }
    catch (problem) { setError(problem instanceof Error ? problem.message : "The recommendation choice could not be saved."); throw problem; }
    finally { setLoading(false); }
  }, [intelligence, token]);
  const refreshIntelligence = useCallback(async () => {
    if (!token || !intelligence) return;
    try { await requestIntelligence(token, intelligence.contributingSkillIds, true); }
    catch (problem) { setAnalysisError(problem instanceof Error ? problem.message : "Another strategy could not be prepared."); throw problem; }
  }, [intelligence, requestIntelligence, token]);
  const cancelAnalysis = useCallback(async () => {
    if (!token || !analysisJob) return;
    try { setAnalysisJob(await api.cancelBackgroundJob(token, analysisJob.id)); }
    catch (problem) { setAnalysisError(problem instanceof Error ? problem.message : "The analysis could not be cancelled."); }
  }, [analysisJob, token]);
  const retryAnalysis = useCallback(async () => {
    if (!token) return;
    try {
      setAnalysisError(null);
      if (!analysisJob) {
        await requestIntelligence(token, intelligence?.contributingSkillIds ?? [], true);
      } else {
        const retried = analysisJob.status === "CANCELLED" || analysisJob.safeErrorCode === "INPUT_CHANGED"
          ? await api.startRecommendationJob(token, intelligence?.contributingSkillIds ?? [], true, crypto.randomUUID())
          : await api.retryBackgroundJob(token, analysisJob.id);
        await pollAnalysis(token, retried);
      }
    } catch (problem) { setAnalysisError(problem instanceof Error ? problem.message : "The analysis could not be retried."); }
  }, [analysisJob, intelligence, pollAnalysis, requestIntelligence, token]);
  const turnIntelligenceIntoProject = useCallback(async () => {
    if (!token || !intelligence) return;
    setLoading(true); setError(null);
    try {
      await api.convertIntersection(token, `recommendation-${intelligence.recommendationId}`, {
        title: `${intelligence.nextSkill.title} proof project`,
        description: intelligence.development.buildProjectSuggestion,
        domain: "ideas",
        contributingNodeIds: intelligence.contributingSkillIds,
      });
      setIntelligence(await api.decideIntelligence(token, intelligence.recommendationId, "EXPLORED", intelligence.selectedStrategyId));
      await load(token);
    } catch (problem) { setError(problem instanceof Error ? problem.message : "The project could not be created."); throw problem; }
    finally { setLoading(false); }
  }, [intelligence, load, token]);
  const search = useCallback(async (query: string) => {
    if (!token) return [];
    try {
      const response = await api.search(token, query);
      return response.nodes.map(apiNodeToUniverse).filter((node) => process.env.NEXT_PUBLIC_EXPANDED_UNIVERSE === "true" || ["SKILL", "PROJECT"].includes(node.type));
    } catch (problem) {
      if (problem instanceof ApiClientError && problem.status === 401) {
        clearAuthentication();
      }
      throw problem;
    }
  }, [clearAuthentication, token]);

  const logout = useCallback(async () => {
    if (!AUTH0_MODE || !auth0Session) {
      clearAuthentication();
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await api.authLogout(auth0Session.csrfHeaderName, auth0Session.csrfToken);
      clearAuthentication();
      window.location.assign(response.logoutUrl);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Auth0 logout failed.");
      setLoading(false);
    }
  }, [auth0Session, clearAuthentication]);

  const exportAccount = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const exported = await api.exportAccount(token);
      const blob = new Blob([JSON.stringify(exported, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `skill-stack-export-${new Date().toISOString().slice(0, 10)}.json`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Your account export could not be prepared.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  const deleteAccount = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      await api.deleteAccount(token);
      clearAuthentication();
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Your account could not be deleted.");
      setLoading(false);
    }
  }, [clearAuthentication, token]);

  const loadPublication = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try { setPublication(await api.publication(token)); }
    catch (problem) { setError(problem instanceof Error ? problem.message : "Publication settings could not be loaded."); }
    finally { setLoading(false); }
  }, [token]);

  const updatePublication = useCallback(async (body: Omit<PublicationConfig, "status" | "publishedAt" | "unpublishedAt">) => {
    if (!token) return;
    setLoading(true); setError(null);
    try { setPublication(await api.updatePublication(token, body)); }
    catch (problem) { setError(problem instanceof Error ? problem.message : "Publication settings could not be saved."); throw problem; }
    finally { setLoading(false); }
  }, [token]);

  const publishProfile = useCallback(async () => {
    if (!token) return;
    setLoading(true); setError(null);
    try { setPublication(await api.publishProfile(token)); }
    catch (problem) { setError(problem instanceof Error ? problem.message : "The public profile could not be published."); throw problem; }
    finally { setLoading(false); }
  }, [token]);

  const unpublishProfile = useCallback(async () => {
    if (!token) return;
    setLoading(true); setError(null);
    try { setPublication(await api.unpublishProfile(token)); }
    catch (problem) { setError(problem instanceof Error ? problem.message : "The public profile could not be withdrawn."); throw problem; }
    finally { setLoading(false); }
  }, [token]);

  const createPublicShare = useCallback(async (label?: string): Promise<PublicationShareCreated> => {
    if (!token) throw new Error("Authentication is required.");
    setLoading(true); setError(null);
    try {
      const created = await api.createPublicShare(token, label);
      setPublication(await api.publication(token));
      return created;
    } catch (problem) { setError(problem instanceof Error ? problem.message : "The share link could not be created."); throw problem; }
    finally { setLoading(false); }
  }, [token]);

  const revokePublicShare = useCallback(async (id: string) => {
    if (!token) return;
    setLoading(true); setError(null);
    try { await api.revokePublicShare(token, id); setPublication(await api.publication(token)); }
    catch (problem) { setError(problem instanceof Error ? problem.message : "The share link could not be revoked."); throw problem; }
    finally { setLoading(false); }
  }, [token]);

  const addTimelineMilestone = useCallback(async (body: { type: TimelineEventType; title: string; description: string | null; occurredOn: string }) => {
    if (!token) return;
    setLoading(true); setError(null);
    try { setPublication(await api.addTimelineMilestone(token, body)); }
    catch (problem) { setError(problem instanceof Error ? problem.message : "The milestone could not be added."); throw problem; }
    finally { setLoading(false); }
  }, [token]);

  const deleteTimelineMilestone = useCallback(async (id: string) => {
    if (!token) return;
    setLoading(true); setError(null);
    try { setPublication(await api.deleteTimelineMilestone(token, id)); }
    catch (problem) { setError(problem instanceof Error ? problem.message : "The milestone could not be deleted."); throw problem; }
    finally { setLoading(false); }
  }, [token]);

  if (!ready) return <LoadingUniverse message="Opening your universe" />;
  if (phaseOneMode) return <SkillStackApp initialData={universe} />;
  if (!token) return <LoginScreen authMode={authConfig.mode} error={error} onAuth0Login={() => window.location.assign(authConfig.loginUrl)} onLogin={async (displayName, demo) => {
    setLoading(true);
    setError(null);
    try {
      const returningSubject = demo ? undefined : window.localStorage.getItem(SUBJECT_KEY) ?? undefined;
      const session = await api.login({ displayName, returningSubject, demo });
      window.localStorage.setItem(TOKEN_KEY, session.accessToken);
      if (!demo) window.localStorage.setItem(SUBJECT_KEY, session.subject);
      setToken(session.accessToken);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Authentication failed.");
    } finally {
      setLoading(false);
    }
  }} busy={loading} />;
  if (!data && loading) return <LoadingUniverse message="Hydrating your graph from PostgreSQL" />;
  if (!data || !graph) return <Unavailable error={error} onRetry={() => void load(token)} />;

  return <SkillStackApp
    initialData={data}
    currentUser={graph?.user.displayName}
    onboardingComplete={graph.user.onboardingComplete}
    defaultDomain={graph.user.defaultDomain as DomainId}
    busy={loading}
    mutationError={error}
    stackSummary={stackSummary}
    develop={develop}
    intelligence={intelligence}
    analysisJob={analysisJob}
    analysisError={analysisError}
    publication={publication}
    onLoadPublication={loadPublication}
    onSavePublication={updatePublication}
    onPublishProfile={publishProfile}
    onUnpublishProfile={unpublishProfile}
    onCreatePublicShare={createPublicShare}
    onRevokePublicShare={revokePublicShare}
    onAddTimelineMilestone={addTimelineMilestone}
    onDeleteTimelineMilestone={deleteTimelineMilestone}
    onCancelAnalysis={cancelAnalysis}
    onRetryAnalysis={retryAnalysis}
    onIntelligenceDecision={decideIntelligence}
    onIntelligenceRefresh={refreshIntelligence}
    onIntelligenceProject={turnIntelligenceIntoProject}
    onSearch={search}
    onCompleteSkillStack={completeSkillStack}
    onImport={createImport}
    onAcceptImport={acceptImport}
    onDiscover={discoverSkills}
    onLogout={() => void logout()}
    onExportAccount={exportAccount}
    onDeleteAccount={deleteAccount}
    onCreateNode={(value) => mutate((activeToken) => api.createNode(activeToken, {
      type: value.type,
      title: value.title,
      description: value.description || null,
      domain: value.domain,
      importance: value.importance,
      depth: value.depth,
      status: "CURIOUS",
      level: value.type === "SKILL" ? "LEARNING" : null,
      monetizable: false,
      learnOrOutsource: null,
      wouldDoForFree: false,
    }))}
    onUpdateNode={(node, value: NodeEditValue) => {
      const source = graph.nodes.find((item) => item.id === node.id)!;
      return mutate((activeToken) => api.updateNode(activeToken, node.id, {
        type: value.type,
        title: value.title,
        description: value.description || null,
        domain: value.domain,
        importance: value.importance,
        depth: value.depth,
        status: value.status,
        level: value.type === "SKILL" ? value.level ?? "LEARNING" : null,
        monetizable: value.type === "SKILL" ? value.monetizable : false,
        learnOrOutsource: value.type === "SKILL" ? value.learnOrOutsource ?? null : null,
        wouldDoForFree: value.type === "INTEREST" ? value.wouldDoForFree : false,
        version: source.version,
      }));
    }}
    onDeleteNode={(node) => mutate((activeToken) => api.deleteNode(activeToken, node.id))}
    onCreateEdge={(selected) => mutate((activeToken) => api.createEdge(activeToken, {
      source: selected[0].id, target: selected[1].id, type: "RELATES_TO", strength: 0.75,
    }))}
    onUpdateEdge={(edge: UniverseEdge, value: RelationshipEditValue) => {
      const source = graph.edges.find((item) => item.id === edge.id)!;
      return mutate((activeToken) => api.updateEdge(activeToken, edge.id, {
        type: value.type,
        strength: value.strength,
        version: source.version,
      }));
    }}
    onDeleteEdge={(edge: UniverseEdge) => mutate((activeToken) => api.deleteEdge(activeToken, edge.id))}
    onCreateProject={(value, selected) => mutate((activeToken) => api.createProject(activeToken, {
      title: value.title, description: value.description || null, domain: value.domain,
      importance: value.importance, status: "PLANNED", conceptNodeIds: selected.map((node) => node.id),
    }))}
    onUpdateProject={(project: ProjectDefinition, value: ProjectEditValue) => {
      const source = graph.projects.find((item) => item.id === project.projectId)!;
      return mutate((activeToken) => api.updateProject(activeToken, source.id, {
        title: value.title,
        description: value.description || null,
        status: value.status,
        startedAt: value.startedAt || null,
        completedAt: value.completedAt || null,
        repositoryUrl: value.repositoryUrl || null,
        liveUrl: value.liveUrl || null,
        version: source.version,
      }));
    }}
    onAddProof={(projectNode, value) => {
      const project = graph.projects.find((item) => item.graphNodeId === projectNode.id);
      if (!project) return Promise.reject(new Error("This project record is unavailable."));
      return mutate((activeToken) => api.addProof(activeToken, project.id, {
        title: value.title, type: "REPOSITORY", description: value.description || null, url: value.url ?? null,
      }));
    }}
    onDeleteProof={(proof: ProofDefinition) => mutate((activeToken) => api.deleteProof(activeToken, proof.id))}
    onIntersection={(action, definition, selected) => mutate((activeToken) => {
      if (action === "convert") return api.convertIntersection(activeToken, definition.id, {
        title: definition.project, description: definition.why, domain: selected[0]?.domain ?? "ideas",
        contributingNodeIds: selected.map((node) => node.id),
      });
      return api.decideIntersection(activeToken, definition.id, {
        title: definition.name, contributingNodeIds: selected.map((node) => node.id), rejected: action === "reject",
      });
    })}
  />;
}

function LoginScreen(props: React.ComponentProps<typeof PublicLanding>) {
  return <PublicLanding {...props} />;
}

function LegacyLoginScreen({ authMode, onLogin, onAuth0Login, busy, error }: {
  authMode: "local" | "auth0";
  onLogin: (name: string, demo: boolean) => Promise<void>;
  onAuth0Login: () => void;
  busy: boolean;
  error: string | null;
}) {
  const [name, setName] = useState("");
  const submit = (event: FormEvent) => { event.preventDefault(); void onLogin(name, false); };
  if (authMode === "auth0") return <main className="auth-shell"><section className="auth-card"><Logo className="ss-logo auth-logo" /><div className="eyebrow">A private Skill Stack</div><h1>Skill Stack</h1><p>Sign in securely to see how your skills combine and what to build next.</p><button className="primary-button" disabled={busy} onClick={onAuth0Login}>{busy ? "Opening…" : "Continue with Auth0"}</button>{error && <p className="inline-error" role="alert">{error}</p>}<small>Your Auth0 client secret remains on the backend.</small></section></main>;
  return <main className="auth-shell"><section className="auth-card"><Logo className="ss-logo auth-logo" /><div className="eyebrow">A private capability map</div><h1>Skill Stack</h1><p>See how your skills combine. Find your next valuable skill. Know how to build it.</p><form onSubmit={submit}><label>How should we address you?<input autoFocus maxLength={120} value={name} onChange={(event) => setName(event.target.value)} placeholder="Your name" /></label><button className="primary-button" disabled={busy}>{busy ? "Opening…" : "Build my Skill Stack"}</button></form><button className="text-button demo-login" disabled={busy} onClick={() => void onLogin("Olli", true)}>Explore the existing demo data</button>{error && <p className="inline-error" role="alert">{error}</p>}<small>Local development uses a signed bearer token.</small></section></main>;
}

function LoadingUniverse({ message }: { message: string }) {
  return <main className="auth-shell"><section className="loading-universe" aria-live="polite"><Logo className="ss-logo auth-logo" /><p>{message}</p></section></main>;
}

function Unavailable({ error, onRetry }: { error: string | null; onRetry: () => void }) {
  return <main className="auth-shell"><section className="auth-card"><div className="eyebrow">Connection interrupted</div><h1>Your stack is still there.</h1><p>{error ?? "The backend could not be reached."}</p><button className="primary-button" onClick={onRetry}>Try again</button></section></main>;
}
