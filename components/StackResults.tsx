"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import type { DevelopResult, DevelopmentRecommendation, IntelligenceDecision, IntelligenceRecommendation, IntelligenceStrategyOption, StackSummary } from "@/lib/api/types";
import type { UniverseNode } from "@/lib/types";

export type ResultView = "SKILLS" | "PATHS" | "PROFILE" | "MAP" | "DEVELOP";

type Props = {
  view: ResultView;
  onView: (view: ResultView) => void;
  summary: StackSummary | null;
  develop: DevelopResult | null;
  intelligence: IntelligenceRecommendation | null;
  skills: UniverseNode[];
  busy?: boolean;
  onDecision?: (decision: IntelligenceDecision, selectedStrategyId?: string | null) => Promise<void>;
  onRefresh?: () => Promise<void>;
  onProject?: () => Promise<void>;
  onShowMap: (skillIds: string[]) => void;
  onEditProfile: () => void;
  onAddSkill?: (title: string) => Promise<void>;
  onEditSkill?: (skill: UniverseNode) => void;
  onDeleteSkill?: (skill: UniverseNode) => Promise<void>;
  onOpenImport?: () => void;
  onOpenList?: () => void;
};

export function StackResults({ view, onView, summary, develop, intelligence, skills, busy, onDecision, onRefresh, onProject, onShowMap, onEditProfile, onAddSkill, onEditSkill, onDeleteSkill, onOpenImport, onOpenList }: Props) {
  return <>
    {view === "SKILLS" && <SkillsView skills={skills} busy={busy} onAddSkill={onAddSkill} onEditSkill={onEditSkill} onDeleteSkill={onDeleteSkill} onOpenImport={onOpenImport} />}
    {view === "PATHS" && <PathsView result={intelligence} busy={busy} onDecision={onDecision} onRefresh={onRefresh} onProject={onProject} onShowMap={onShowMap} onView={onView} />}
    {view === "MAP" && <MapOverlay summary={summary} intelligence={intelligence} onOpenList={onOpenList} />}
    {view === "PROFILE" && <ProfileResult summary={summary} intelligence={intelligence} skills={skills} onEdit={onEditProfile} />}
    {view === "DEVELOP" && <DevelopResultView result={develop} intelligence={intelligence} onView={onView} />}
  </>;
}

function SkillsView({ skills, busy, onAddSkill, onEditSkill, onDeleteSkill, onOpenImport }: Pick<Props, "skills" | "busy" | "onAddSkill" | "onEditSkill" | "onDeleteSkill" | "onOpenImport">) {
  const [query, setQuery] = useState("");
  const [entry, setEntry] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const filtered = useMemo(() => skills.filter((skill) => skill.name.toLowerCase().includes(query.trim().toLowerCase())), [query, skills]);
  const groups = useMemo(() => groupSkills(filtered), [filtered]);

  const add = async (event: FormEvent) => {
    event.preventDefault();
    const title = entry.trim();
    if (!title || !onAddSkill) return;
    if (skills.some((skill) => skill.name.localeCompare(title, undefined, { sensitivity: "base" }) === 0)) {
      setMessage(`${title} is already in your skills.`);
      return;
    }
    await onAddSkill(title);
    setEntry("");
    setMessage(`${title} added.`);
  };

  return <section className="result-surface app-page skills-page">
    <PageHeading eyebrow={`${skills.length} skill${skills.length === 1 ? "" : "s"}`} title="Your skills" description="Keep the collection accurate. Paths and development guidance use these skills as their foundation." />
    <div className="skills-toolbar">
      <form className="skill-add" onSubmit={(event) => void add(event)}>
        <label htmlFor="new-skill">Add a skill</label>
        <div><input id="new-skill" value={entry} maxLength={180} onChange={(event) => setEntry(event.target.value)} placeholder="Type a skill, for example observability" /><button className="primary-button" disabled={busy || !entry.trim()}>Add skill</button></div>
      </form>
      <div className="skills-toolbar-actions">
        <label htmlFor="skill-filter">Search your skills</label>
        <input id="skill-filter" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Filter skills" />
        {onOpenImport && <button className="secondary-button" onClick={onOpenImport}>Add from text</button>}
      </div>
    </div>
    {message && <p className="inline-status" role="status">{message}</p>}
    <div className="skill-groups">
      {groups.map(([group, items]) => <section key={group} className="skill-group"><header><h2>{group}</h2><span>{items.length}</span></header><div className="skill-chip-list">{items.map((skill) => <article className="skill-chip" key={skill.id}><button className="skill-chip-main" onClick={() => onEditSkill?.(skill)}><strong>{skill.name}</strong><small>{skillLevel(skill)}</small></button>{onDeleteSkill && <button className="skill-remove" aria-label={`Remove ${skill.name}`} disabled={busy} onClick={() => { if (window.confirm(`Remove ${skill.name} from your skills?`)) void onDeleteSkill(skill); }}>×</button>}</article>)}</div></section>)}
      {filtered.length === 0 && <div className="compact-empty"><strong>No matching skills</strong><p>Try another search or add a custom skill.</p></div>}
    </div>
    <footer className="page-note">Select a skill to edit its level, importance, payability belief, description, and proof links.</footer>
  </section>;
}

function PathsView({ result, busy, onDecision, onRefresh, onProject, onShowMap, onView }: { result: IntelligenceRecommendation | null; busy?: boolean; onDecision?: Props["onDecision"]; onRefresh?: Props["onRefresh"]; onProject?: Props["onProject"]; onShowMap: Props["onShowMap"]; onView: Props["onView"] }) {
  const [selectedPathId, setSelectedPathId] = useState<string | null>(null);
  const [skillIndex, setSkillIndex] = useState(0);
  useEffect(() => { if (result) setSelectedPathId(result.selectedStrategyId ?? result.recommendedStrategyId); }, [result]);
  if (!result) return <section className="result-surface app-page"><PageHeading eyebrow="Paths" title="Paths are temporarily unavailable" description="Your saved skills, profile, map, and deterministic development guidance remain available." /></section>;
  const selectedPath = result.strategies.find((path) => path.id === selectedPathId) ?? result.strategies[0];
  const skillIdeas = [{ title: result.nextSkill.title, tradeoff: displayText(result.nextSkill.whyNow), uncertainty: "Recommended now" }, ...result.nextSkill.alternatives.map((item) => ({ ...item, tradeoff: displayText(item.tradeoff) }))];
  const activeSkill = skillIdeas[skillIndex % skillIdeas.length];

  return <section className="result-surface app-page paths-page">
    <div className="page-heading-row"><PageHeading eyebrow={result.engine === "AI_ASSISTED" ? "AI-assisted, grounded in your skills" : "Grounded in your skills"} title="Paths for you" description="Compare practical ways to combine what you already know. Open a path to inspect the evidence and tradeoffs." /><div className="heading-actions"><IntelligenceState result={result} surface="Paths" />{onRefresh && <button className="secondary-button" disabled={busy} onClick={() => void onRefresh()}>{busy ? "Refreshing" : "Regenerate paths"}</button>}</div></div>
    <div className="path-card-grid">{result.strategies.slice(0, 3).map((path) => <PathCard key={path.id} path={path} active={path.id === selectedPath.id} onOpen={() => setSelectedPathId(path.id)} />)}</div>
    <section className="path-detail" aria-label="Selected path details">
      <header><div><span className="section-kicker">Selected path</span><h2>{selectedPath.title}</h2><p>{displayText(selectedPath.summary)}</p></div><button className="primary-button" disabled={busy || selectedPath.id === result.selectedStrategyId} onClick={() => void onDecision?.("MODIFIED", selectedPath.id)}>{selectedPath.id === result.selectedStrategyId ? "Selected" : "Choose this path"}</button></header>
      <div className="path-detail-grid">
        <DetailBlock title="What it involves"><p>{displayText(selectedPath.synergy)}</p></DetailBlock>
        <DetailBlock title="Why it matches"><ul>{selectedPath.contributions.map((item) => <li key={item.skillId}><strong>{item.title}</strong><span>{displayText(item.contribution)}</span></li>)}</ul></DetailBlock>
        <DetailBlock title="Skills to develop"><p><strong>{selectedPath.missingCapability}</strong></p><p>{displayText(selectedPath.tradeoff)}</p></DetailBlock>
        <DetailBlock title="Potential opportunities"><ul>{selectedPath.plausibleApplications.map((item) => <li key={item}>{displayText(item)}</li>)}</ul></DetailBlock>
        <DetailBlock title="Practical next steps"><p>{displayText(selectedPath.firstAction)}</p><p>{displayText(result.nextSkill.evidenceOutcome)}</p></DetailBlock>
      </div>
      <footer><button className="text-button" onClick={() => onShowMap(result.contributingSkillIds)}>Show supporting skills on map</button>{onProject && <button className="text-button" onClick={() => void onProject()}>Turn into a project</button>}</footer>
    </section>
    <section className="individual-skills" aria-labelledby="individual-skills-title"><header><div><span className="section-kicker">A path is optional</span><h2 id="individual-skills-title">Individual skills to explore</h2></div><button className="secondary-button" onClick={() => setSkillIndex((current) => (current + 1) % skillIdeas.length)}>Show another skill</button></header><article><div><strong>{activeSkill.title}</strong><p>{activeSkill.tradeoff}</p><small>{activeSkill.uncertainty}</small></div><button className="primary-button" onClick={() => onView("DEVELOP")}>Open development plan</button></article></section>
  </section>;
}

function PathCard({ path, active, onOpen }: { path: IntelligenceStrategyOption; active: boolean; onOpen: () => void }) {
  return <article className={`path-card ${active ? "active" : ""}`}><span>{path.recommended ? "Recommended" : pretty(path.strategy)}</span><h2>{path.title}</h2><p>{displayText(path.summary)}</p><div className="path-skill-row"><small>Existing</small>{path.contributions.map((item) => <i key={item.skillId}>{item.title}</i>)}</div><div className="path-skill-row needed"><small>Develop</small><i>{path.missingCapability}</i></div><button onClick={onOpen}>Explore path <span aria-hidden="true">↗</span></button></article>;
}

function ProfileResult({ summary, intelligence, skills, onEdit }: { summary: StackSummary | null; intelligence: IntelligenceRecommendation | null; skills: UniverseNode[]; onEdit: () => void }) {
  if (!summary) return <section className="result-surface app-page"><PageHeading eyebrow="Who you are" title="Building your profile" description="Your saved skills are still available while this summary loads." /></section>;
  const ranked = [...skills].sort((a, b) => Number(Boolean(b.proofOfWork)) - Number(Boolean(a.proofOfWork)) || b.importance - a.importance);
  const core = ranked.slice(0, 4);
  const supporting = ranked.slice(4);
  const title = core.length > 1 ? `${core[0].name} with ${core[1].name}` : core[0]?.name ?? "Your professional profile";
  return <section className="result-surface app-page profile-page">
    <div className="page-heading-row"><PageHeading eyebrow="Who you are" title={title} description={displayText(summary.sentence)} /><button className="secondary-button" onClick={onEdit}>Edit skills</button></div>
    <div className="profile-layout">
      <section className="profile-summary-panel"><span className="section-kicker">Professional profile</span><h2>{displayText(intelligence?.summary ?? summary.stageReason)}</h2>{intelligence && <IntelligenceState result={intelligence} surface="Profile" />}<div className="profile-stage"><span>Current focus</span><strong>{pretty(summary.recommendedStage)}</strong><p>{displayText(summary.stageReason)}</p></div></section>
      <section className="profile-facts"><ProfileList title="Key strengths" items={core.map((skill) => ({ title: skill.name, detail: `${skillLevel(skill)}, importance ${skill.importance}/10` }))} /><ProfileList title="Skill patterns" items={summary.patterns.map((pattern) => ({ title: pattern.label, detail: displayText(pattern.evidence) }))} /><ProfileList title="Supporting skills" items={supporting.length ? supporting.map((skill) => ({ title: skill.name, detail: skillLevel(skill) })) : [{ title: "Add another skill", detail: "A broader collection will reveal more combinations." }]} /></section>
    </div>
    <section className="profile-combinations"><header><span className="section-kicker">Observed combinations</span><h2>How your skills currently connect</h2></header><div>{summary.quadrants.length ? summary.quadrants.map((quadrant) => <article key={quadrant.id}><span>{quadrant.label}</span><strong>{quadrant.title}</strong>{quadrant.signals.map((signal) => <p key={signal}>{signal}</p>)}</article>) : <article><strong>Add another skill</strong><p>Two skills are needed before a combination can be compared.</p></article>}</div></section>
    <p className="page-note">This profile describes supplied skills and project evidence. It does not infer personality, certification, or market demand.</p>
  </section>;
}

function MapOverlay({ summary, intelligence, onOpenList }: { summary: StackSummary | null; intelligence: IntelligenceRecommendation | null; onOpenList?: () => void }) {
  return <><section className="map-toolbar" aria-label="Skill map view"><div><span className="section-kicker">Skill map</span><strong>{displayText(intelligence?.headline ?? "Your skills and their relationships")}</strong></div><div className="view-toggle"><button className="active">Graph</button><button onClick={onOpenList}>List</button></div></section>{summary && <aside className="map-stack-summary" aria-label="Skill Stack map summary"><span className="section-kicker">Current direction</span><strong>{summary.recommendedStage.replaceAll("_", " ")}</strong><p>{displayText(summary.stageReason)}</p>{intelligence && <small>{intelligence.contributingSkillIds.length} contributing skill{intelligence.contributingSkillIds.length === 1 ? "" : "s"} highlighted</small>}</aside>}</>;
}

type ResourceTab = "Mentors" | "Education" | "Communities" | "Networks" | "Events";

function DevelopResultView({ result, intelligence, onView }: { result: DevelopResult | null; intelligence: IntelligenceRecommendation | null; onView: Props["onView"] }) {
  const [activeTab, setActiveTab] = useState<ResourceTab>("Education");
  const resourceGroups = useMemo(() => classifyResources(result), [result]);
  const availableTabs = (["Mentors", "Education", "Communities", "Networks", "Events"] as ResourceTab[]).filter((tab) => resourceGroups[tab].length > 0);
  useEffect(() => { if (availableTabs.length && !availableTabs.includes(activeTab)) setActiveTab(availableTabs[0]); }, [activeTab, availableTabs]);
  if (!result) return <section className="result-surface app-page"><PageHeading eyebrow="Develop" title="Ways to develop" description="Your saved profile remains available while development guidance loads." /></section>;
  const selectedPath = intelligence?.strategies.find((path) => path.id === (intelligence.selectedStrategyId ?? intelligence.recommendedStrategyId)) ?? intelligence?.strategies[0];
  const skillsToDevelop = intelligence ? [{ title: intelligence.nextSkill.title, reason: displayText(intelligence.nextSkill.whyNow), primary: true }, ...intelligence.nextSkill.alternatives.map((item) => ({ title: item.title, reason: displayText(item.tradeoff), primary: false }))] : [{ title: result.nextSkill.title, reason: displayText(result.nextSkill.reasons.join(" ")), primary: true }];

  return <section className="result-surface app-page develop-page">
    <div className="page-heading-row"><PageHeading eyebrow="Develop" title="Ways to develop" description="Use verified resources and small actions that fit your current direction." />{intelligence && <IntelligenceState result={intelligence} surface="Develop" />}</div>
    <section className="direction-summary"><div><span className="section-kicker">Current selected direction</span><h2>{selectedPath?.title ?? intelligence?.headline ?? result.nextSkill.title}</h2><p>{displayText(selectedPath?.summary ?? intelligence?.summary ?? result.nextSkill.reasons.join(" "))}</p></div><button className="secondary-button" onClick={() => onView("PATHS")}>Change path</button></section>
    <div className="develop-top-grid"><section><header><span className="section-kicker">Skills to develop</span><h2>Build the missing capability</h2></header><div className="development-rows">{skillsToDevelop.map((skill) => <article key={skill.title}><span>{skill.primary ? "Next" : "Alternative"}</span><strong>{skill.title}</strong><p>{skill.reason}</p></article>)}</div></section><section><header><span className="section-kicker">Leverage and opportunities</span><h2>What this could support</h2></header><ul className="opportunity-list">{(selectedPath?.plausibleApplications ?? result.nextSkill.levers).map((item) => <li key={item}>{item}</li>)}</ul></section></div>
    <section className="resource-section" aria-labelledby="resources-title"><header><div><span className="section-kicker">Verified catalog</span><h2 id="resources-title">Resources</h2></div>{availableTabs.length > 0 && <div className="resource-tabs" role="tablist">{availableTabs.map((tab) => <button role="tab" aria-selected={activeTab === tab} className={activeTab === tab ? "active" : ""} key={tab} onClick={() => setActiveTab(tab)}>{tab}<span>{resourceGroups[tab].length}</span></button>)}</div>}</header>{availableTabs.length ? <div className="resource-list" role="tabpanel">{resourceGroups[activeTab].map((item) => <RecommendationCard key={item.id} item={item} />)}</div> : <div className="compact-empty"><strong>No verified resource match yet</strong><p>The catalog has no trustworthy course, mentor, community, network, or event for this direction. Skill Stack will not invent one.</p></div>}</section>
    {(result.moves.length > 0 || result.build.length > 0) && <section className="action-section"><header><span className="section-kicker">Apply and prove</span><h2>Practical next steps</h2></header><div className="resource-list action-list">{[...result.moves, ...result.build].map((item) => <RecommendationCard key={item.id} item={item} />)}</div></section>}
    <div className="result-caveats">{result.caveats.map((caveat) => <p key={caveat}>{caveat}</p>)}</div>
  </section>;
}

function RecommendationCard({ item }: { item: DevelopmentRecommendation }) {
  const body = <><div><span>{item.subsection?.replaceAll("_", " ") ?? item.section}</span><strong>{item.title}</strong></div><p>{displayText(item.reason)}</p><div className="resource-meta"><i>{item.formatLabel}</i><i>{item.costLabel}</i><i>{item.lever}</i></div>{item.url && <b aria-hidden="true">↗</b>}</>;
  return item.url ? <a className="recommendation-card" href={item.url} target="_blank" rel="noreferrer">{body}</a> : <article className="recommendation-card">{body}</article>;
}

function PageHeading({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return <header className="page-heading"><span className="section-kicker">{eyebrow}</span><h1 id={`${title.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-")}-title`}>{title}</h1><p>{description}</p></header>;
}

function IntelligenceState({ result, surface }: { result: IntelligenceRecommendation; surface: string }) {
  return <div className="intelligence-state" aria-label={`${surface} analysis provenance`}><span>{result.engine.replaceAll("_", " ")}</span><small>{result.provenance.costState.replaceAll("_", " ")}</small><small>{result.provenance.cacheHit ? "Reused saved analysis" : "Generated for this input version"}</small></div>;
}

function DetailBlock({ title, children }: { title: string; children: React.ReactNode }) {
  return <section><h3>{title}</h3>{children}</section>;
}

function ProfileList({ title, items }: { title: string; items: { title: string; detail: string }[] }) {
  return <section><h2>{title}</h2>{items.map((item) => <article key={`${item.title}-${item.detail}`}><strong>{item.title}</strong><p>{item.detail}</p></article>)}</section>;
}

function skillLevel(skill: UniverseNode) {
  return pretty(skill.effectiveLevel ?? skill.level ?? "LEARNING");
}

function pretty(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/^./, (character) => character.toUpperCase());
}

function displayText(value: string) {
  return value.replaceAll(" — ", ". ").replaceAll("—", "-");
}

function groupSkills(skills: UniverseNode[]): [string, UniverseNode[]][] {
  const groups = new Map<string, UniverseNode[]>();
  const groupFor = (name: string) => {
    const value = name.toLowerCase();
    if (/(backend|frontend|java|python|javascript|typescript|react|spring|code|software|program)/.test(value)) return "Programming";
    if (/(system|cloud|devops|infra|network|security|database|postgres|architecture|reliability)/.test(value)) return "Systems & infrastructure";
    if (/(product|business|sales|marketing|strategy|finance|commercial|customer)/.test(value)) return "Product & business";
    if (/(data|ai|machine learning|analytics|statistics|research)/.test(value)) return "Data & AI";
    if (/(lead|writing|communication|teaching|management|facilitat|design)/.test(value)) return "Leadership & communication";
    return "Other skills";
  };
  skills.forEach((skill) => { const group = groupFor(skill.name); groups.set(group, [...(groups.get(group) ?? []), skill]); });
  return [...groups.entries()].map(([group, items]) => [group, [...items].sort((a, b) => a.name.localeCompare(b.name))]);
}

function classifyResources(result: DevelopResult | null): Record<ResourceTab, DevelopmentRecommendation[]> {
  const groups: Record<ResourceTab, DevelopmentRecommendation[]> = { Mentors: [], Education: [], Communities: [], Networks: [], Events: [] };
  if (!result) return groups;
  groups.Education = result.education;
  result.networks.forEach((item) => {
    const text = `${item.title} ${item.reason} ${item.formatLabel}`.toLowerCase();
    if (/mentor|coaching/.test(text)) groups.Mentors.push(item);
    else if (/community|discord|skool|slack|forum/.test(text)) groups.Communities.push(item);
    else if (/event|hackathon|conference|meetup/.test(text)) groups.Events.push(item);
    else groups.Networks.push(item);
  });
  return groups;
}
