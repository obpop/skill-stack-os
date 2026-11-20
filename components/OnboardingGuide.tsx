"use client";

import { useEffect, useMemo, useState } from "react";
import type { CareerRole, HelpTarget, ImportJob, SkillDirection, WorkPreference } from "@/lib/api/types";
import type { SkillLevel, UniverseNode } from "@/lib/types";

export type SkillDraft = { id: string; title: string; level: SkillLevel; monetizable: boolean; importance: number };
export type InterestDraft = { id: string; title: string; wouldDoForFree: boolean };
export type OnboardingDraft = {
  displayName: string; city: string; country: string; skills: SkillDraft[]; interests: InterestDraft[];
  currentRole: CareerRole; targetRole: CareerRole; buildOnTheSide: boolean; timelineYears: number;
  skillDirection: SkillDirection; focusSkillTitles: string[]; workPreference: WorkPreference; helpTarget: HelpTarget;
};
export type ImportReviewItem = { id: string; title: string; type: "SKILL" | "INTEREST" | "PROJECT"; level: SkillLevel | null; evidence: string; provenance: "DETERMINISTIC_MATCH" | "AI_SUGGESTION_UNVERIFIED"; uncertainty: string; selected: boolean; duplicate: boolean };

type Props = {
  initialName: string; nodes: UniverseNode[]; busy?: boolean; error?: string | null;
  onComplete: (draft: OnboardingDraft) => Promise<void>;
  onImport: (file: File) => Promise<ImportJob>;
  onAcceptImport: (job: ImportJob, items: ImportReviewItem[]) => Promise<void>;
};

const STORAGE_KEY = "skill-stack-onboarding-draft";
const roles: CareerRole[] = ["STUDENT", "EMPLOYEE", "ENTREPRENEUR", "FREELANCER"];
const steps = ["Welcome", "Basics", "Skills", "Direction", "Building"];
const uid = () => Math.random().toString(36).slice(2);
const pretty = (value: string) => value.toLowerCase().replaceAll("_", " ").replace(/^./, (char) => char.toUpperCase());
const defaultDraft = (name: string): OnboardingDraft => ({ displayName: name, city: "", country: "", skills: [], interests: [], currentRole: "EMPLOYEE", targetRole: "EMPLOYEE", buildOnTheSide: false, timelineYears: 3, skillDirection: "NOT_SURE", focusSkillTitles: [], workPreference: "REMOTE", helpTarget: "BUSINESSES" });

export function OnboardingGuide({ initialName, nodes, busy, error, onComplete, onImport, onAcceptImport }: Props) {
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<OnboardingDraft>(() => {
    if (typeof window === "undefined") return defaultDraft(initialName);
    try { return { ...defaultDraft(initialName), ...JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "{}") }; }
    catch { return defaultDraft(initialName); }
  });
  const [entry, setEntry] = useState("");
  const [importJob, setImportJob] = useState<ImportJob | null>(null);
  const [review, setReview] = useState<ImportReviewItem[]>([]);
  const [importMessage, setImportMessage] = useState<string | null>(null);
  const ownedSkills = useMemo(() => nodes.filter((node) => node.type === "SKILL"), [nodes]);
  const totalSkillCount = ownedSkills.length + draft.skills.length;

  useEffect(() => { window.localStorage.setItem(STORAGE_KEY, JSON.stringify(draft)); }, [draft]);

  const addSkill = () => {
    const title = entry.trim();
    if (!title || [...ownedSkills.map((node) => node.name), ...draft.skills.map((skill) => skill.title)].some((value) => value.localeCompare(title, undefined, { sensitivity: "base" }) === 0)) return;
    setDraft((current) => ({ ...current, skills: [...current.skills, { id: uid(), title, level: "LEARNING", monetizable: false, importance: 6 }] }));
    setEntry("");
  };
  const next = async () => {
    if (step === 1 && !draft.displayName.trim()) return;
    if (step === 2 && totalSkillCount === 0) return;
    if (step < 3) { setEntry(""); setStep((current) => current + 1); return; }
    const focusNeeded = draft.skillDirection === "SPECIALIZE" ? 1 : draft.skillDirection === "COMBINE" ? 2 : 0;
    if (focusNeeded && draft.focusSkillTitles.length !== focusNeeded) return;
    setStep(4);
    try { await onComplete(draft); window.localStorage.removeItem(STORAGE_KEY); }
    catch { /* parent error state is rendered with a retry path */ }
  };
  const upload = async (file?: File) => {
    if (!file) return;
    setImportMessage(null);
    try {
      const job = await onImport(file);
      setImportJob(job);
      setReview(job.proposals.filter((proposal) => proposal.type !== "INTEREST").map((proposal) => ({ id: proposal.id, title: proposal.title, type: proposal.type, level: proposal.suggestedLevel, evidence: proposal.evidence, provenance: proposal.provenance, uncertainty: proposal.uncertainty, selected: true, duplicate: proposal.duplicate })));
    } catch (problem) { setImportMessage(problem instanceof Error ? problem.message : "The text could not be reviewed."); }
  };
  const acceptImport = async () => {
    if (!importJob) return;
    const selected = review.filter((item) => item.selected);
    await onAcceptImport(importJob, selected);
    setImportMessage(`${selected.length} reviewed item${selected.length === 1 ? "" : "s"} added or merged.`);
    setImportJob(null); setReview([]);
  };

  return <div className="onboarding-shell" role="dialog" aria-modal="true" aria-labelledby="onboarding-title">
    <div className="onboarding-progress" aria-label={`Step ${step + 1} of ${steps.length}`}><span>{String(step + 1).padStart(2, "0")}</span><div>{steps.map((label, index) => <i key={label} className={index <= step ? "active" : ""} />)}</div><strong>{steps[step]}</strong></div>

    {step === 0 && <section className="onboarding-panel welcome-panel"><div className="eyebrow">Skill Stack · private by default</div><h1 id="onboarding-title">See how your skills combine and what to build next.</h1><p>Add what you can do and where you want to go. You will leave with one justified next skill and practical ways to build it.</p><button className="primary-button" onClick={() => setStep(1)}>Start</button></section>}

    {step === 1 && <section className="onboarding-panel form-panel"><div className="eyebrow">The basics</div><h1 id="onboarding-title">Make this stack yours.</h1><label>Display name<input autoFocus maxLength={120} value={draft.displayName} onChange={(event) => setDraft({ ...draft, displayName: event.target.value })} /></label><div className="field-pair"><label>City <small>So we can suggest local programs and networks.</small><input maxLength={120} value={draft.city} onChange={(event) => setDraft({ ...draft, city: event.target.value })} placeholder="Optional" /></label><label>Country<input maxLength={120} value={draft.country} onChange={(event) => setDraft({ ...draft, country: event.target.value })} placeholder="Optional" /></label></div></section>}

    {step === 2 && <section className="onboarding-panel form-panel"><div className="eyebrow">Your foundation</div><h1 id="onboarding-title">What can you do?</h1><p>Add at least one skill. Keep it truthful; the useful recommendation comes from the combination.</p>{ownedSkills.length > 0 && <div className="existing-signals"><span>Already in your private graph</span>{ownedSkills.map((node) => <strong key={node.id}>{node.name}</strong>)}</div>}<div className="entry-row"><input autoFocus value={entry} onChange={(event) => setEntry(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); addSkill(); } }} placeholder="e.g. Backend development with Spring Boot, social media content creation" /><button onClick={addSkill}>Add skill</button></div><div className="draft-list">{draft.skills.map((skill, index) => <article key={skill.id}><div><strong>{skill.title}</strong><button aria-label={`Remove ${skill.title}`} onClick={() => setDraft({ ...draft, skills: draft.skills.filter((item) => item.id !== skill.id) })}>×</button></div><label>Level<select value={skill.level} onChange={(event) => { const skills = [...draft.skills]; skills[index] = { ...skill, level: event.target.value as SkillLevel }; setDraft({ ...draft, skills }); }}><option value="LEARNING">Learning</option><option value="GOOD">Better than most people</option><option value="TOP_25">Top 25% in my field</option></select></label><label className="check-row"><input type="checkbox" checked={skill.monetizable} onChange={(event) => { const skills = [...draft.skills]; skills[index] = { ...skill, monetizable: event.target.checked }; setDraft({ ...draft, skills }); }} />Someone would pay for this</label><label>Importance <strong>{skill.importance}/10</strong><input type="range" min="1" max="10" value={skill.importance} onChange={(event) => { const skills = [...draft.skills]; skills[index] = { ...skill, importance: Number(event.target.value) }; setDraft({ ...draft, skills }); }} /></label></article>)}</div><label className="upload-control">Upload a text about yourself <small>.txt or .md, up to 2 MB. Nothing is added before review.</small><input type="file" accept=".txt,.md,text/plain,text/markdown" onChange={(event) => void upload(event.target.files?.[0])} /></label>{importJob && <ImportReview items={review} setItems={setReview} onAccept={() => void acceptImport()} busy={busy} />}{importMessage && <p className="inline-status" role="status">{importMessage}</p>}{totalSkillCount === 0 && <p className="inline-error">Add or import at least one skill to continue.</p>}</section>}

    {step === 3 && <DirectionStep draft={draft} setDraft={setDraft} availableSkills={[...new Set([...ownedSkills.map((node) => node.name), ...draft.skills.map((skill) => skill.title)])]} />}
    {step === 4 && <section className="onboarding-panel building-panel"><div className="stack-pulse" aria-hidden="true"><i /><i /><i /></div><div className="eyebrow">Building your stack</div><h1 id="onboarding-title">Saving the choices behind your map.</h1><p>Your profile, skill levels, project links, and preferences are being saved and calculated.</p>{busy && <span className="building-status" role="status">Writing your private stack…</span>}{error && <><p className="inline-error" role="alert">{error}</p><button className="primary-button" onClick={() => setStep(3)}>Review and retry</button></>}</section>}
    {step > 0 && step < 4 && <footer className="onboarding-actions"><button className="text-button" onClick={() => setStep((current) => current - 1)}>Back</button><button className="primary-button" disabled={busy || (step === 1 && !draft.displayName.trim()) || (step === 2 && totalSkillCount === 0)} onClick={() => void next()}>{step === 3 ? "Build my stack" : "Next"}</button></footer>}
  </div>;
}

function DirectionStep({ draft, setDraft, availableSkills }: { draft: OnboardingDraft; setDraft: (draft: OnboardingDraft) => void; availableSkills: string[] }) {
  const focusLimit = draft.skillDirection === "COMBINE" ? 2 : draft.skillDirection === "SPECIALIZE" ? 1 : 0;
  return <section className="onboarding-panel direction-panel"><div className="eyebrow">Direction</div><h1 id="onboarding-title">Where should this stack take you?</h1>
    <fieldset><legend>1. Are you a…</legend><div className="choice-grid">{roles.map((role) => <button type="button" key={role} className={draft.currentRole === role ? "selected" : ""} onClick={() => setDraft({ ...draft, currentRole: role, targetRole: role, buildOnTheSide: false })}>{pretty(role)}</button>)}</div></fieldset>
    <fieldset><legend>2. Where do you want to go?</legend><div className="choice-grid"><button type="button" className={!draft.buildOnTheSide && draft.targetRole === draft.currentRole ? "selected" : ""} onClick={() => setDraft({ ...draft, targetRole: draft.currentRole, buildOnTheSide: false })}>Stay as I am</button>{roles.filter((role) => role !== draft.currentRole).map((role) => <button type="button" key={role} className={!draft.buildOnTheSide && draft.targetRole === role ? "selected" : ""} onClick={() => setDraft({ ...draft, targetRole: role, buildOnTheSide: false })}>{pretty(role)}</button>)}<button type="button" className={draft.buildOnTheSide ? "selected" : ""} onClick={() => setDraft({ ...draft, targetRole: draft.currentRole, buildOnTheSide: true })}>Keep my role and build on the side</button></div></fieldset>
    <fieldset><legend>3. How long is your timeline? <strong>{draft.timelineYears} year{draft.timelineYears === 1 ? "" : "s"}</strong></legend><input type="range" min="1" max="10" value={draft.timelineYears} onChange={(event) => setDraft({ ...draft, timelineYears: Number(event.target.value) })} /></fieldset>
    <fieldset><legend>4. Where do you want to go with your skills?</legend><select value={draft.skillDirection} onChange={(event) => setDraft({ ...draft, skillDirection: event.target.value as SkillDirection, focusSkillTitles: [] })}><option value="DEEPEN_FIELD">Go deeper in my field</option><option value="SPECIALIZE">Specialize in one skill</option><option value="COMBINE">Combine skills into something unique</option><option value="SWITCH_FIELD">Switch field</option><option value="NOT_SURE">Not sure yet</option></select>{focusLimit > 0 && <div className="focus-choices">{availableSkills.map((skill) => <label key={skill}><input type="checkbox" checked={draft.focusSkillTitles.includes(skill)} onChange={(event) => { const next = event.target.checked ? [...draft.focusSkillTitles, skill].slice(-focusLimit) : draft.focusSkillTitles.filter((item) => item !== skill); setDraft({ ...draft, focusSkillTitles: next }); }} />{skill}</label>)}</div>}</fieldset>
    <fieldset><legend>5. Where can you work?</legend><div className="choice-grid">{(["STAY_IN_CITY", "OPEN_TO_MOVE", "REMOTE"] as WorkPreference[]).map((value) => <button type="button" key={value} className={draft.workPreference === value ? "selected" : ""} onClick={() => setDraft({ ...draft, workPreference: value })}>{pretty(value)}</button>)}</div></fieldset>
    <fieldset><legend>6. Who would you most like to help?</legend><div className="choice-grid">{(["BUSINESSES", "CREATORS", "STUDENTS", "A_CAUSE"] as HelpTarget[]).map((value) => <button type="button" key={value} className={draft.helpTarget === value ? "selected" : ""} onClick={() => setDraft({ ...draft, helpTarget: value })}>{pretty(value)}</button>)}</div></fieldset>
  </section>;
}

export function ImportReview({ items, setItems, onAccept, busy }: { items: ImportReviewItem[]; setItems: (items: ImportReviewItem[]) => void; onAccept: () => void; busy?: boolean }) {
  const selectedCount = items.filter((item) => item.selected).length;
  return <section className="import-review" aria-label="Review imported suggestions"><div className="eyebrow">Review before adding</div><h2>{items.length ? "We found possible skills and projects." : "No supported skills or projects were found."}</h2><p>Every suggestion is unverified until you approve it. Check the source excerpt, edit the title and level, or deselect it.</p>{items.map((item, index) => <article key={item.id}><input aria-label={`Select ${item.title}`} type="checkbox" checked={item.selected} onChange={(event) => { const next = [...items]; next[index] = { ...item, selected: event.target.checked }; setItems(next); }} /><div><input value={item.title} maxLength={180} onChange={(event) => { const next = [...items]; next[index] = { ...item, title: event.target.value }; setItems(next); }} /><small>{item.type}{item.duplicate ? " · merges with an existing item" : " · new suggestion"} · {item.provenance === "AI_SUGGESTION_UNVERIFIED" ? "AI suggestion" : "exact text match"}</small><blockquote>{item.evidence}</blockquote><small>{item.uncertainty}</small></div>{item.type === "SKILL" && <select aria-label={`Level for ${item.title}`} value={item.level ?? "LEARNING"} onChange={(event) => { const next = [...items]; next[index] = { ...item, level: event.target.value as SkillLevel }; setItems(next); }}><option value="LEARNING">Learning</option><option value="GOOD">Better than most people</option><option value="TOP_25">Top 25% in my field</option></select>}</article>)}<button className="secondary-button" disabled={busy || selectedCount === 0} onClick={onAccept}>Add {selectedCount} selected item{selectedCount === 1 ? "" : "s"}</button></section>;
}
