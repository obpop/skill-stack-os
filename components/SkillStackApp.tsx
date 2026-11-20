"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { buildUniverse, chooseIntersection, domainMeta, findNodeByName, universe } from "@/data/universe";
import { connectedNeighborhood, createGraph } from "@/lib/graph";
import type { DiscoveryPhase, DomainId, IntersectionDefinition, NodeStatus, ProjectDefinition, ProofDefinition, UniverseData, UniverseEdge, UniverseNode } from "@/lib/types";
import { DiscoverySheet } from "@/components/DiscoverySheet";
import { GraphCanvas, type GraphHandle } from "@/components/GraphCanvas";
import { NodeSheet, type NodeEditValue, type ProjectEditValue, type RelationshipEditValue } from "@/components/NodeSheet";
import { ImportReview, OnboardingGuide, type ImportReviewItem, type OnboardingDraft } from "@/components/OnboardingGuide";
import { SearchCommand } from "@/components/SearchCommand";
import { StackResults, type ResultView } from "@/components/StackResults";
import { UniverseComposer, type ComposerMode, type ComposerValue } from "@/components/UniverseComposer";
import { AnalysisProgress } from "@/components/AnalysisProgress";
import { PublicProfilePanel } from "@/components/PublicProfilePanel";
import { Logo } from "@/components/site/Logo";
import type { BackgroundJob, DevelopResult, ImportJob, IntelligenceDecision, IntelligenceRecommendation, PublicationConfig, PublicationOwner, PublicationShareCreated, StackDiscovery, StackSummary, TimelineEventType } from "@/lib/api/types";

const expandedUniverse = process.env.NEXT_PUBLIC_EXPANDED_UNIVERSE === "true";
const typeFilters = expandedUniverse ? ["SKILL", "KNOWLEDGE", "IDEA", "PROJECT"] : ["SKILL", "PROJECT"];
const statusFilters: NodeStatus[] = ["CURIOUS", "EXPLORING", "ACTIVE", "DEVELOPED", "ARCHIVED"];
const phaseCopy: Record<DiscoveryPhase, string> = {
  idle: "",
  reducing: "Holding the chosen worlds in view",
  retrieving: "Following relationships through your universe",
  converging: "Testing where these worlds meet",
  revealed: "A plausible intersection emerged",
};

type Props = {
  initialData?: UniverseData;
  currentUser?: string;
  onboardingComplete?: boolean;
  defaultDomain?: DomainId;
  busy?: boolean;
  mutationError?: string | null;
  stackSummary?: StackSummary | null;
  develop?: DevelopResult | null;
  intelligence?: IntelligenceRecommendation | null;
  analysisJob?: BackgroundJob | null;
  analysisError?: string | null;
  publication?: PublicationOwner | null;
  onLogout?: () => void;
  onExportAccount?: () => Promise<void>;
  onDeleteAccount?: () => Promise<void>;
  onCreateNode?: (value: ComposerValue) => Promise<void>;
  onUpdateNode?: (node: UniverseNode, value: NodeEditValue) => Promise<void>;
  onDeleteNode?: (node: UniverseNode) => Promise<void>;
  onCreateEdge?: (selected: UniverseNode[]) => Promise<void>;
  onUpdateEdge?: (edge: UniverseEdge, value: RelationshipEditValue) => Promise<void>;
  onDeleteEdge?: (edge: UniverseEdge) => Promise<void>;
  onUpdateProject?: (project: ProjectDefinition, value: ProjectEditValue) => Promise<void>;
  onCreateProject?: (value: ComposerValue, selected: UniverseNode[]) => Promise<void>;
  onAddProof?: (project: UniverseNode, value: ComposerValue) => Promise<void>;
  onDeleteProof?: (proof: ProofDefinition) => Promise<void>;
  onIntersection?: (action: "save" | "reject" | "convert", definition: IntersectionDefinition, selected: UniverseNode[]) => Promise<void>;
  onSearch?: (query: string) => Promise<UniverseNode[]>;
  onCompleteSkillStack?: (draft: OnboardingDraft) => Promise<void>;
  onImport?: (file: File) => Promise<ImportJob>;
  onAcceptImport?: (job: ImportJob, items: ImportReviewItem[]) => Promise<void>;
  onDiscover?: (selected: UniverseNode[]) => Promise<StackDiscovery>;
  onIntelligenceDecision?: (decision: IntelligenceDecision, selectedStrategyId?: string | null) => Promise<void>;
  onIntelligenceRefresh?: () => Promise<void>;
  onIntelligenceProject?: () => Promise<void>;
  onCancelAnalysis?: () => void;
  onRetryAnalysis?: () => void;
  onLoadPublication?: () => Promise<void>;
  onSavePublication?: (body: Omit<PublicationConfig, "status" | "publishedAt" | "unpublishedAt">) => Promise<void>;
  onPublishProfile?: () => Promise<void>;
  onUnpublishProfile?: () => Promise<void>;
  onCreatePublicShare?: (label?: string) => Promise<PublicationShareCreated>;
  onRevokePublicShare?: (id: string) => Promise<void>;
  onAddTimelineMilestone?: (body: { type: TimelineEventType; title: string; description: string | null; occurredOn: string }) => Promise<void>;
  onDeleteTimelineMilestone?: (id: string) => Promise<void>;
};

export function SkillStackApp({ initialData, currentUser, onboardingComplete, defaultDomain = "ideas", busy, mutationError, stackSummary = null, develop = null, intelligence = null, analysisJob = null, analysisError = null, publication = null, onLogout, onExportAccount, onDeleteAccount, onCreateNode, onUpdateNode, onDeleteNode, onCreateEdge, onUpdateEdge, onDeleteEdge, onCreateProject, onUpdateProject, onAddProof, onDeleteProof, onIntersection, onSearch, onCompleteSkillStack, onImport, onAcceptImport, onDiscover, onIntelligenceDecision, onIntelligenceRefresh, onIntelligenceProject, onCancelAnalysis, onRetryAnalysis, onLoadPublication, onSavePublication, onPublishProfile, onUnpublishProfile, onCreatePublicShare, onRevokePublicShare, onAddTimelineMilestone, onDeleteTimelineMilestone }: Props) {
  const legacyMode = onboardingComplete === undefined && !onCompleteSkillStack;
  const data = useMemo(() => {
    let source = initialData;
    if (!source) {
      if (typeof window === "undefined") source = universe;
      else {
        const rawCount = new URLSearchParams(window.location.search).get("nodes");
        const requested = rawCount === null ? Number.NaN : Number(rawCount);
        source = Number.isFinite(requested) && requested >= 0 ? buildUniverse(requested) : universe;
      }
    }
    if (expandedUniverse) return source;
    const visibleTypes = legacyMode ? ["SKILL", "INTEREST", "PROJECT"] : ["SKILL", "PROJECT"];
    const visibleNodes = source.nodes.filter((node) => visibleTypes.includes(node.type));
    const visibleIds = new Set(visibleNodes.map((node) => node.id));
    return { ...source, nodes: visibleNodes, edges: source.edges.filter((edge) => visibleIds.has(edge.source) && visibleIds.has(edge.target)) };
  }, [initialData, legacyMode]);
  const topology = useMemo(() => createGraph(data), [data]);
  const graphRef = useRef<GraphHandle>(null);
  const accountButtonRef = useRef<HTMLButtonElement>(null);
  const accountDialogRef = useRef<HTMLElement>(null);
  const timers = useRef<number[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [hovered, setHovered] = useState<string | null>(null);
  const [focused, setFocused] = useState<string | null>(null);
  const [searchResult, setSearchResult] = useState<string | null>(null);
  const [activeDomains, setActiveDomains] = useState<Set<DomainId>>(new Set());
  const [activeTypes, setActiveTypes] = useState<Set<string>>(new Set());
  const [activeStatuses, setActiveStatuses] = useState<Set<NodeStatus>>(new Set());
  const [minimumImportance, setMinimumImportance] = useState(1);
  const [proofFilter, setProofFilter] = useState<"any" | "with" | "without">("any");
  const [year, setYear] = useState(2026);
  const [phase, setPhase] = useState<DiscoveryPhase>("idle");
  const [intersection, setIntersection] = useState<IntersectionDefinition | null>(null);
  const [intersectionSaved, setIntersectionSaved] = useState(false);
  const [converted, setConverted] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [publicationOpen, setPublicationOpen] = useState(false);
  const [showIntro, setShowIntro] = useState(!onboardingComplete);
  const [composer, setComposer] = useState<ComposerMode | null>(null);
  const [resultView, setResultView] = useState<ResultView>(() => {
    if (legacyMode) return "MAP";
    if (typeof window === "undefined" || !onboardingComplete) return "SKILLS";
    const stored = window.localStorage.getItem("skill-stack-result-view") as ResultView | null;
    return stored && ["SKILLS", "PATHS", "PROFILE", "MAP", "DEVELOP"].includes(stored) ? stored : "PATHS";
  });
  const [importOpen, setImportOpen] = useState(false);
  const [importJob, setImportJob] = useState<ImportJob | null>(null);
  const [importItems, setImportItems] = useState<ImportReviewItem[]>([]);
  const showOnboarding = Boolean(onCompleteSkillStack && onImport && onAcceptImport && !onboardingComplete);

  const changeResultView = (view: ResultView) => {
    setResultView(view);
    window.localStorage.setItem("skill-stack-result-view", view);
  };

  const nodesById = useMemo(() => new Map(data.nodes.map((node) => [node.id, node])), [data.nodes]);
  const selectedNodes = useMemo(
    () => [...selected].map((id) => nodesById.get(id)).filter(Boolean) as UniverseNode[],
    [nodesById, selected],
  );
  const focusedNode = focused ? nodesById.get(focused) ?? null : null;
  const focusedProject = focusedNode?.type === "PROJECT" ? data.projects.find((project) => project.id === focusedNode.id) : undefined;
  const focusedProofs = focusedProject?.projectId ? (data.proofs ?? []).filter((proof) => proof.projectId === focusedProject.projectId) : [];
  const focusedProvenProjects = focusedNode?.provenByProjectIds?.map((id) => data.projects.find((project) => project.projectId === id)).filter(Boolean) as ProjectDefinition[] | undefined;
  const hoveredNode = hovered ? nodesById.get(hovered) ?? null : null;
  const relevant = useMemo(() => {
    if (phase === "idle") return new Set<string>();
    const result = connectedNeighborhood(topology, selected);
    intersection?.supporting.forEach((name) => {
      const node = findNodeByName(name, data);
      if (node) result.add(node.id);
    });
    return result;
  }, [data, intersection, phase, selected, topology]);

  const neighbors = useMemo(() => {
    if (!focusedNode || !topology.hasNode(focusedNode.id)) return [];
    return topology.neighbors(focusedNode.id).map((id) => nodesById.get(id)).filter(Boolean) as UniverseNode[];
  }, [focusedNode, nodesById, topology]);
  const relationships = useMemo(() => {
    if (!focusedNode) return [];
    return data.edges.flatMap((edge) => {
      if (edge.source !== focusedNode.id && edge.target !== focusedNode.id) return [];
      const neighbor = nodesById.get(edge.source === focusedNode.id ? edge.target : edge.source);
      return neighbor ? [{ edge, neighbor }] : [];
    });
  }, [data.edges, focusedNode, nodesById]);
  const visibleNodeCount = useMemo(() => data.nodes.filter((node) =>
    (activeDomains.size === 0 || activeDomains.has(node.domain)) &&
    (activeTypes.size === 0 || activeTypes.has(node.type)) &&
    (activeStatuses.size === 0 || (node.status !== undefined && activeStatuses.has(node.status))) &&
    node.importance >= minimumImportance &&
    (proofFilter === "any" || (proofFilter === "with" ? node.proofOfWork === true : node.proofOfWork !== true)) &&
    node.year <= year
  ).length, [activeDomains, activeStatuses, activeTypes, data.nodes, minimumImportance, proofFilter, year]);

  const clearTimers = useCallback(() => {
    timers.current.forEach(window.clearTimeout);
    timers.current = [];
  }, []);

  useEffect(() => () => clearTimers(), [clearTimers]);

  useEffect(() => {
    if (!accountOpen) return;
    accountDialogRef.current?.querySelector<HTMLElement>("button:not([disabled]), [href], input:not([disabled])")?.focus();
  }, [accountOpen]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        if (publicationOpen) {
          setPublicationOpen(false);
          window.requestAnimationFrame(() => accountButtonRef.current?.focus());
          return;
        }
        if (accountOpen) {
          setAccountOpen(false);
          window.requestAnimationFrame(() => accountButtonRef.current?.focus());
          return;
        }
        if (importOpen) {
          setImportOpen(false);
          return;
        }
        if (composer) {
          setComposer(null);
          return;
        }
        if (phase !== "idle") resetUniverse();
        else {
          setFocused(null);
          setFiltersOpen(false);
          setDirectoryOpen(false);
          setHelpOpen(false);
        }
      }
      if (event.key.toLowerCase() === "d" && (event.metaKey || event.ctrlKey) && selected.size >= 2) {
        event.preventDefault();
        void discover();
      }
      if (event.key === "?" && document.activeElement?.tagName !== "INPUT") setHelpOpen(true);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const toggleSelected = (id: string) => {
    if (phase !== "idle") return;
    const node = nodesById.get(id);
    if (!expandedUniverse && node?.type !== "SKILL") return;
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else if (next.size < (expandedUniverse ? 6 : 3)) next.add(id);
      return next;
    });
  };

  const handleNodeClick = (id: string) => {
    if (!nodesById.has(id)) return;
    setShowIntro(false);
    setFocused(id);
    toggleSelected(id);
  };

  const focusNode = (id: string, searched = false) => {
    setFocused(id);
    if (searched) setSearchResult(id);
    graphRef.current?.focusNode(id);
    setShowIntro(false);
  };

  const selectDemo = () => {
    const names = ["Backend development with Spring Boot", "AI", "Investing", "Economics"];
    const ids = names.map((name) => findNodeByName(name, data)?.id).filter(Boolean) as string[];
    setSelected(new Set(ids));
    setFocused(null);
    setShowIntro(false);
  };

  const discover = async () => {
    if (selectedNodes.length < 2 || selectedNodes.length > 3 || phase !== "idle") return;
    clearTimers();
    let match: IntersectionDefinition;
    if (onDiscover) {
      const result = await onDiscover(selectedNodes);
      match = { id: result.id, name: result.title, required: selectedNodes.map((node) => node.name), supporting: [], why: result.why, project: result.build, explore: [result.career], learn: result.learn, direction: result.career };
    } else {
      const rejected = new Set((data.intersectionDecisions ?? []).filter((decision) => decision.decision === "REJECTED").map((decision) => decision.definitionKey));
      match = chooseIntersection(selectedNodes, data.intersections, rejected);
    }
    const persistedDecision = data.intersectionDecisions?.find((decision) => decision.definitionKey === match.id);
    setIntersection(match);
    setIntersectionSaved(persistedDecision?.decision === "SAVED" || persistedDecision?.decision === "CONVERTED");
    setConverted(persistedDecision?.decision === "CONVERTED");
    setFocused(null);
    setSearchResult(null);
    setPhase("reducing");
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const steps: [DiscoveryPhase, number][] = reduced
      ? [["retrieving", 80], ["converging", 160], ["revealed", 260]]
      : [["retrieving", 750], ["converging", 1800], ["revealed", 2950]];
    steps.forEach(([nextPhase, delay]) => {
      timers.current.push(window.setTimeout(() => setPhase(nextPhase), delay));
    });
  };

  const resetUniverse = () => {
    clearTimers();
    setPhase("idle");
    setFocused(null);
    setSearchResult(null);
    setSelected(new Set());
    setIntersection(intersectionSaved ? intersection : null);
    setConverted(false);
    graphRef.current?.reset();
  };

  const rejectIntersection = () => {
    setIntersectionSaved(false);
    setIntersection(null);
    resetUniverse();
  };

  const persistIntersection = async (action: "save" | "reject" | "convert") => {
    if (intersection && onIntersection) await onIntersection(action, intersection, selectedNodes);
    if (action === "save") setIntersectionSaved(true);
    if (action === "convert") {
      setConverted(true);
      setIntersectionSaved(true);
    }
    if (action === "reject") rejectIntersection();
  };

  const submitComposer = async (value: ComposerValue) => {
    if (composer === "node" && onCreateNode) await onCreateNode(value);
    if (composer === "edge" && onCreateEdge) await onCreateEdge(selectedNodes);
    if (composer === "project" && onCreateProject) await onCreateProject(value, selectedNodes);
    if (composer === "proof" && focusedNode && onAddProof) await onAddProof(focusedNode, value);
    setComposer(null);
    setSelected(new Set());
  };

  const toggleDomain = (domain: DomainId) => {
    setActiveDomains((current) => {
      const next = new Set(current);
      next.has(domain) ? next.delete(domain) : next.add(domain);
      return next;
    });
  };

  const toggleType = (type: string) => {
    setActiveTypes((current) => {
      const next = new Set(current);
      next.has(type) ? next.delete(type) : next.add(type);
      return next;
    });
  };

  const toggleStatus = (status: NodeStatus) => {
    setActiveStatuses((current) => {
      const next = new Set(current);
      next.has(status) ? next.delete(status) : next.add(status);
      return next;
    });
  };

  return (
    <main className={legacyMode ? "universe-shell legacy-mode" : `universe-shell ss-app-theme${resultView === "MAP" ? " ss-map-view" : ""}${showOnboarding ? " ss-onboarding" : ""}`}>
      {(legacyMode || resultView === "MAP") && <GraphCanvas
        ref={graphRef}
        data={data}
        selected={selected}
        hovered={hovered}
        focused={focused}
        searchResult={searchResult}
        relevant={relevant}
        activeDomains={activeDomains}
        activeTypes={activeTypes}
        activeStatuses={activeStatuses}
        minimumImportance={minimumImportance}
        proofFilter={proofFilter}
        year={year}
        phase={phase}
        intersection={intersection}
        intersectionSaved={intersectionSaved}
        onNodeClick={handleNodeClick}
        onHover={setHovered}
        onStageClick={() => {
          if (phase === "idle") setFocused(null);
        }}
      />}

      <header className="topbar">
        <button className="brand" onClick={() => { resetUniverse(); changeResultView("PATHS"); }} aria-label="Open Skill Stack paths">
          <Logo />
          <span>Skill Stack</span>
        </button>
        {!showOnboarding && onboardingComplete && <nav className="app-primary-nav" aria-label="Skill Stack results">
          {([["SKILLS", "Skills"], ["PATHS", "Paths"], ["PROFILE", "Who you are"], ["MAP", "Skill map"], ["DEVELOP", "Develop"]] as [ResultView, string][]).map(([id, label]) => <button key={id} className={resultView === id ? "active" : ""} aria-current={resultView === id ? "page" : undefined} onClick={() => { if (id !== "MAP") resetUniverse(); changeResultView(id); }}>{label}</button>)}
        </nav>}
        <SearchCommand nodes={data.nodes} onSearch={onSearch} onSelect={(node) => focusNode(node.id, true)} />
        <div className="topbar-actions">
          <button className="icon-button" onClick={() => setDirectoryOpen(true)} aria-label="Open structured graph directory">☷</button>
          <button className="icon-button" onClick={() => setHelpOpen(true)} aria-label="Open keyboard help">?</button>
          <button ref={accountButtonRef} className="profile-orbit" aria-label={`Open ${currentUser ?? "Olli"}'s private account controls`} aria-expanded={accountOpen} onClick={() => setAccountOpen(true)}><span>{(currentUser ?? "Olli").slice(0, 1).toUpperCase()}</span></button>
        </div>
      </header>

      {!showOnboarding && onboardingComplete && <AnalysisProgress job={analysisJob} error={analysisError} onCancel={onCancelAnalysis} onRetry={onRetryAnalysis} />}

      {resultView === "MAP" && <nav className="left-rail" aria-label="Skill map actions">
        <button className="rail-item" onClick={() => setFiltersOpen((value) => !value)}><span className="rail-symbol">◌</span><span>Filter</span></button>
        <button className="rail-item" onClick={() => {
          setActiveTypes(new Set(["PROJECT"]));
          setFiltersOpen(false);
        }}><span className="rail-symbol">◇</span><span>Projects</span></button>
        {onCreateNode && <button className="rail-item" onClick={() => setComposer("node")}><span className="rail-symbol">+</span><span>Add</span></button>}
        {onImport && <button className="rail-item" onClick={() => setImportOpen(true)}><span className="rail-symbol">↥</span><span>Add from text</span></button>}
      </nav>}

      {!showOnboarding && onboardingComplete && <StackResults view={resultView} onView={changeResultView} summary={stackSummary} develop={develop} intelligence={intelligence} skills={data.nodes.filter((node) => node.type === "SKILL")} busy={busy} onDecision={onIntelligenceDecision} onRefresh={onIntelligenceRefresh} onProject={onIntelligenceProject} onShowMap={(skillIds) => { setSelected(new Set(skillIds)); changeResultView("MAP"); }} onEditProfile={() => changeResultView("SKILLS")} onAddSkill={onCreateNode ? (title) => onCreateNode({ title, description: "", domain: defaultDomain, type: "SKILL", importance: 7, depth: 5 }) : undefined} onEditSkill={(skill) => setFocused(skill.id)} onDeleteSkill={onDeleteNode} onOpenImport={onImport ? () => setImportOpen(true) : undefined} onOpenList={() => setDirectoryOpen(true)} />}

      {filtersOpen && (
        <section className="filter-popover" aria-label="Graph filters">
          <div className="popover-heading"><div><div className="eyebrow">View</div><h3>Filter your Skill Stack</h3></div><button onClick={() => setFiltersOpen(false)}>×</button></div>
          {expandedUniverse && <div className="filter-group">
            <div className="eyebrow">Worlds</div>
            {Object.entries(domainMeta).map(([id, meta]) => (
              <button key={id} className={activeDomains.has(id as DomainId) ? "filter-option active" : "filter-option"} onClick={() => toggleDomain(id as DomainId)}>
                <i style={{ background: meta.color }} />{meta.label}<span>{activeDomains.has(id as DomainId) ? "✓" : ""}</span>
              </button>
            ))}
          </div>}
          <div className="filter-group compact">
            <div className="eyebrow">Kinds</div>
            <div className="filter-chips">{(legacyMode ? ["SKILL", "INTEREST", "PROJECT"] : typeFilters).map((type) => <button key={type} className={activeTypes.has(type) ? "active" : ""} onClick={() => toggleType(type)}>{type.toLowerCase()}</button>)}</div>
          </div>
          <div className="filter-group compact">
            <div className="eyebrow">Status</div>
            <div className="filter-chips">{statusFilters.map((status) => <button key={status} className={activeStatuses.has(status) ? "active" : ""} onClick={() => toggleStatus(status)}>{status.toLowerCase()}</button>)}</div>
          </div>
          <div className="filter-group compact importance-filter">
            <label htmlFor="minimum-importance"><span className="eyebrow">Minimum importance</span><strong>{minimumImportance}/10</strong></label>
            <input id="minimum-importance" aria-label="Minimum importance" type="range" min="1" max="10" value={minimumImportance} onChange={(event) => setMinimumImportance(Number(event.target.value))} />
          </div>
          <div className="filter-group compact">
            <div className="eyebrow">Proof of work</div>
            <div className="filter-chips proof-filter">
              {(["any", "with", "without"] as const).map((value) => <button key={value} className={proofFilter === value ? "active" : ""} onClick={() => setProofFilter(value)}>{value === "any" ? "any" : value === "with" ? "with proof" : "without proof"}</button>)}
            </div>
          </div>
          <p className="filter-count" aria-live="polite">Showing {visibleNodeCount} of {data.nodes.length} signals</p>
          <button className="text-button reset-filters" onClick={() => { setActiveDomains(new Set()); setActiveTypes(new Set()); setActiveStatuses(new Set()); setMinimumImportance(1); setProofFilter("any"); }}>Clear filters</button>
        </section>
      )}

      {resultView === "MAP" && showIntro && data.nodes.length > 0 && !showOnboarding && (
        <section className="intro-note">
          <div className="eyebrow">Your Skill Stack · {data.nodes.filter((node) => node.type === "SKILL").length} skills</div>
          <h1>See how your skills combine.<br />Find what to build next.</h1>
          <p>Skills sit at the center. Projects connect practice and proof to the capabilities they use.</p>
          <div className="intro-actions">
            <button className="primary-button" onClick={selectDemo}>Try a constellation</button>
            <button className="text-button" onClick={() => setShowIntro(false)}>Explore my map</button>
          </div>
        </section>
      )}

      {phase !== "idle" && phase !== "revealed" && (
        <div className={`reasoning-state phase-${phase}`} aria-live="polite">
          <div className="reasoning-line"><i /><i /><i /></div>
          <span>{phaseCopy[phase]}</span>
          <small>{phase === "retrieving" ? `${relevant.size} related signals active` : "Graph traversal in progress"}</small>
        </div>
      )}

      {hoveredNode && !focusedNode && phase === "idle" && (
        <div className="hover-context">
          <span className="hover-dot" style={{ background: domainMeta[hoveredNode.domain].color }} />
          <div><strong>{hoveredNode.name}</strong><small>{hoveredNode.type.replaceAll("_", " ")} · {topology.hasNode(hoveredNode.id) ? topology.degree(hoveredNode.id) : 0} connections</small></div>
        </div>
      )}

      {focusedNode && phase === "idle" && (
        <NodeSheet
          node={focusedNode}
          neighbors={neighbors}
          relationships={relationships}
          project={focusedProject}
          proofs={focusedProofs}
          provenProjects={focusedProvenProjects}
          selected={selected.has(focusedNode.id)}
          onClose={() => setFocused(null)}
          onToggleSelect={() => toggleSelected(focusedNode.id)}
          onFocusNeighbor={focusNode}
          onUpdate={onUpdateNode ? (value) => onUpdateNode(focusedNode, value) : undefined}
          onUpdateRelationship={onUpdateEdge}
          onDeleteRelationship={onDeleteEdge}
          onUpdateProject={onUpdateProject}
          onDeleteProof={onDeleteProof}
          onDelete={onDeleteNode ? async () => {
            await onDeleteNode(focusedNode);
            setFocused(null);
            setHovered((current) => current === focusedNode.id ? null : current);
            setSearchResult((current) => current === focusedNode.id ? null : current);
            setSelected((current) => {
              const next = new Set(current);
              next.delete(focusedNode.id);
              return next;
            });
          } : undefined}
          onAddProof={focusedNode.type === "PROJECT" && onAddProof ? () => setComposer("proof") : undefined}
        />
      )}

      {intersection && phase === "revealed" && (
        <DiscoverySheet
          intersection={intersection}
          contributing={selectedNodes}
          saved={intersectionSaved}
          converted={converted}
          busy={busy}
          onSave={() => void persistIntersection("save")}
          onReject={() => void persistIntersection("reject")}
          onConvert={() => void persistIntersection("convert")}
        />
      )}

      {resultView === "MAP" && selectedNodes.length > 0 && phase === "idle" && (
        <div className="selection-dock" aria-label="Selected concepts">
          <div className="selection-copy"><span className="selection-count">{selectedNodes.length}</span><div><strong>{selectedNodes.length === 1 ? "One skill selected" : "A skill combination is forming"}</strong><small>{selectedNodes.length < 2 ? "Select one more skill to discover an intersection" : `${selectedNodes.length} of 3 skills selected`}</small></div></div>
          <div className="selected-tokens">
            {selectedNodes.map((node) => <button key={node.id} onClick={() => toggleSelected(node.id)}>{node.name}<span>×</span></button>)}
          </div>
          <button className="discover-button" disabled={selectedNodes.length < 2 || selectedNodes.length > 3} onClick={() => void discover()}>
            <span className="discover-glyph">✣</span>
            Discover intersections
            <kbd>⌘D</kbd>
          </button>
          {onCreateEdge && <button className="secondary-button connect-button" disabled={selectedNodes.length !== 2 || busy} onClick={() => setComposer("edge")}>Connect selection</button>}
          {onCreateProject && <button className="secondary-button connect-button" disabled={busy} onClick={() => setComposer("project")}>Create project</button>}
        </div>
      )}

      {phase === "revealed" && <button className="return-universe" onClick={resetUniverse}>← Return to Skill Stack</button>}

      {resultView === "MAP" && <div className="graph-controls" aria-label="Graph view controls">
        <button onClick={() => graphRef.current?.zoomIn()} aria-label="Zoom in">+</button>
        <button onClick={() => graphRef.current?.zoomOut()} aria-label="Zoom out">−</button>
        <button onClick={() => graphRef.current?.reset()} aria-label="Fit universe">⌗</button>
      </div>}

      {expandedUniverse && <div className="timeline-control">
        <div className="timeline-label"><span className="eyebrow">Universe in time</span><strong>{year}</strong></div>
        <input aria-label="Universe year" type="range" min="2024" max="2026" value={year} onChange={(event) => setYear(Number(event.target.value))} />
        <div className="timeline-years"><span>2024</span><span>2025</span><span>2026</span></div>
      </div>}

      {resultView === "MAP" && <div className="graph-legend" aria-label="Graph legend">
        <span><i className="legend-node" />Skill</span>
        {legacyMode && <span><i className="legend-interest" />Interest</span>}
        <span><i className="legend-project" />Project / project-backed proof</span>
      </div>}

      {showOnboarding && onCompleteSkillStack && onImport && onAcceptImport && (
        <OnboardingGuide
          initialName={currentUser ?? ""}
          nodes={data.nodes}
          busy={busy}
          error={mutationError}
          onComplete={onCompleteSkillStack}
          onImport={onImport}
          onAcceptImport={onAcceptImport}
        />
      )}

      {data.nodes.length === 0 && !showOnboarding && (
        <section className="empty-universe">
          <span className="empty-orbit"><i /><i /></span>
          <div className="eyebrow">An unwritten Skill Stack</div>
          <h1>Begin with one skill you can name.</h1>
          <p>Your map grows through skills and project-backed proof. It is not a personality test.</p>
          <button className="primary-button" onClick={() => setComposer("node")}>Add your first skill</button>
        </section>
      )}

      {directoryOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setDirectoryOpen(false)}>
          <section className="directory-panel" role="dialog" aria-modal="true" aria-label="Structured graph directory" onMouseDown={(event) => event.stopPropagation()}>
            <div className="popover-heading"><div><div className="eyebrow">Accessible graph view</div><h2>Skill Stack directory</h2></div><button aria-label="Close" onClick={() => setDirectoryOpen(false)}>×</button></div>
            <p>Browse the same private map without the canvas. Every entry includes its kind, level or status, and relationship count.</p>
            {(expandedUniverse ? Object.entries(domainMeta).map(([id, meta]) => ({ id, label: meta.label, color: meta.color, nodes: data.nodes.filter((node) => node.domain === id && node.type !== "DOMAIN") })) : [
              { id: "SKILL", label: "Skills", color: "#78A694", nodes: data.nodes.filter((node) => node.type === "SKILL") },
              ...(legacyMode ? [{ id: "INTEREST", label: "Interests", color: "#68808A", nodes: data.nodes.filter((node) => node.type === "INTEREST") }] : []),
              { id: "PROJECT", label: "Projects", color: "#A9E5CC", nodes: data.nodes.filter((node) => node.type === "PROJECT") },
            ]).map((group) => (
              <details key={group.id} open={!expandedUniverse}>
                <summary><i style={{ background: group.color }} />{group.label}<span>{group.nodes.length}</span></summary>
                <div className="directory-nodes">
                  {group.nodes.map((node) => {
                    const connections = topology.hasNode(node.id) ? topology.degree(node.id) : 0;
                    return <button key={node.id} aria-label={`${node.name}, ${node.type.replaceAll("_", " ").toLowerCase()}, ${(node.status ?? "curious").toLowerCase()}, ${connections} connections`} onClick={() => { setDirectoryOpen(false); focusNode(node.id, true); }}>
                      {node.name}<small>{node.type.replaceAll("_", " ")} / {(node.effectiveLevel ?? node.status ?? "CURIOUS").toLowerCase().replaceAll("_", " ")} / {connections} links</small>
                    </button>;
                  })}
                </div>
              </details>
            ))}
          </section>
        </div>
      )}

      {helpOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setHelpOpen(false)}>
          <section className="help-panel" role="dialog" aria-modal="true" aria-label="Keyboard help" onMouseDown={(event) => event.stopPropagation()}>
            <div className="popover-heading"><div><div className="eyebrow">Navigation</div><h2>Move through your Skill Stack</h2></div><button onClick={() => setHelpOpen(false)}>×</button></div>
            <dl><div><dt><kbd>/</kbd></dt><dd>Search and focus a concept</dd></div><div><dt><kbd>Esc</kbd></dt><dd>Return or close the active layer</dd></div><div><dt><kbd>Ctrl D</kbd></dt><dd>Discover from selected concepts</dd></div><div><dt><kbd>?</kbd></dt><dd>Open this guide</dd></div></dl>
          </section>
        </div>
      )}
      {accountOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setAccountOpen(false)}>
          <section ref={accountDialogRef} className="account-panel" role="dialog" aria-modal="true" aria-labelledby="account-title" onMouseDown={(event) => event.stopPropagation()}>
            <div className="popover-heading"><div><div className="eyebrow">Private by default</div><h2 id="account-title">Your account</h2></div><button aria-label="Close account controls" onClick={() => setAccountOpen(false)}>×</button></div>
            <p>Your profile, skills, projects, proof, imports, recommendations and job history stay private to your account. Raw text imports are processed in memory and are not stored.</p>
            <p>AI runs only when the server owner explicitly enables it within a spending limit. Generated recommendations are labeled and deterministic recommendations remain available.</p>
            <div className="account-actions">
              {onLoadPublication && <button className="primary-button" disabled={busy} onClick={() => { setAccountOpen(false); setPublicationOpen(true); }}>Preview and publish my Skill Stack</button>}
              {onExportAccount && <button className="secondary-button" disabled={busy} onClick={() => void onExportAccount()}>Download my data</button>}
              {onLogout && <button className="secondary-button" disabled={busy} onClick={onLogout}>Sign out</button>}
            </div>
            {onDeleteAccount && <div className="account-danger"><strong>Delete this account</strong><p>This permanently removes your saved Skill Stack data and cannot be undone.</p><button disabled={busy} onClick={() => { if (window.confirm("Permanently delete your Skill Stack account and all saved data?")) void onDeleteAccount(); }}>Delete account</button></div>}
          </section>
        </div>
      )}
      {publicationOpen && onLoadPublication && onSavePublication && onPublishProfile && onUnpublishProfile && onCreatePublicShare && onRevokePublicShare && onAddTimelineMilestone && onDeleteTimelineMilestone && (
        <PublicProfilePanel
          publication={publication}
          skills={data.nodes.filter((node) => node.type === "SKILL")}
          projects={data.projects}
          proofs={data.proofs ?? []}
          intelligence={intelligence}
          busy={busy}
          error={mutationError}
          onClose={() => { setPublicationOpen(false); window.requestAnimationFrame(() => accountButtonRef.current?.focus()); }}
          onReload={onLoadPublication}
          onSave={onSavePublication}
          onPublish={onPublishProfile}
          onUnpublish={onUnpublishProfile}
          onCreateShare={onCreatePublicShare}
          onRevokeShare={onRevokePublicShare}
          onAddMilestone={onAddTimelineMilestone}
          onDeleteMilestone={onDeleteTimelineMilestone}
        />
      )}
      {importOpen && onImport && onAcceptImport && <div className="modal-backdrop" role="presentation" onMouseDown={() => setImportOpen(false)}><section className="import-modal" role="dialog" aria-modal="true" aria-label="Add skills and projects from text" onMouseDown={(event) => event.stopPropagation()}><div className="popover-heading"><div><div className="eyebrow">Private, reviewed import</div><h2>Add from text</h2></div><button onClick={() => setImportOpen(false)}>×</button></div><p>Choose a .txt or .md file up to 2 MB. Skill Stack discards the raw text after bounded extraction and adds nothing until you approve it. AI extraction is used only when explicitly enabled within budget.</p><label className="upload-control">Choose text<input type="file" accept=".txt,.md,text/plain,text/markdown" onChange={(event) => { const file = event.target.files?.[0]; if (!file) return; void onImport(file).then((job) => { setImportJob(job); setImportItems(job.proposals.filter((proposal) => proposal.type !== "INTEREST").map((proposal) => ({ id: proposal.id, title: proposal.title, type: proposal.type, level: proposal.suggestedLevel, evidence: proposal.evidence, provenance: proposal.provenance, uncertainty: proposal.uncertainty, selected: true, duplicate: proposal.duplicate }))); }); }} /></label>{importJob && <ImportReview items={importItems} setItems={setImportItems} busy={busy} onAccept={() => { void onAcceptImport(importJob, importItems).then(() => { setImportOpen(false); setImportJob(null); setImportItems([]); }); }} />}</section></div>}
      <footer className="brand-attribution">Skill Stack</footer>
      {mutationError && <div className="mutation-error" role="alert">{mutationError}</div>}
      {composer && <UniverseComposer mode={composer} selected={composer === "proof" && focusedNode ? [focusedNode] : selectedNodes} defaultDomain={defaultDomain} busy={busy} onClose={() => setComposer(null)} onSubmit={submitComposer} />}
    </main>
  );
}
