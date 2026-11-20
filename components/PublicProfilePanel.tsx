"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import type { IntelligenceRecommendation, PublicationConfig, PublicationOwner, PublicationShareCreated, PublicTheme, TimelineEventType } from "@/lib/api/types";
import type { ProjectDefinition, ProofDefinition, UniverseNode } from "@/lib/types";

type UpdateBody = Omit<PublicationConfig, "status" | "publishedAt" | "unpublishedAt">;

type Props = {
  publication: PublicationOwner | null;
  skills: UniverseNode[];
  projects: ProjectDefinition[];
  proofs: ProofDefinition[];
  intelligence: IntelligenceRecommendation | null;
  busy?: boolean;
  error?: string | null;
  onClose: () => void;
  onReload: () => Promise<void>;
  onSave: (body: UpdateBody) => Promise<void>;
  onPublish: () => Promise<void>;
  onUnpublish: () => Promise<void>;
  onCreateShare: (label?: string) => Promise<PublicationShareCreated>;
  onRevokeShare: (id: string) => Promise<void>;
  onAddMilestone: (body: { type: TimelineEventType; title: string; description: string | null; occurredOn: string }) => Promise<void>;
  onDeleteMilestone: (id: string) => Promise<void>;
};

const visibilityFields: { key: keyof PublicationConfig; label: string; help: string }[] = [
  { key: "showDisplayName", label: "Display name", help: "Your account display name." },
  { key: "showSentence", label: "Stack sentence", help: "A sentence built only from selected skills." },
  { key: "showSkillLevels", label: "Levels and project backing", help: "Self-assessed levels and limited PROVEN meaning." },
  { key: "showIntersections", label: "Combination explanations", help: "Only the recommendation paths selected below." },
  { key: "showNextSkill", label: "Development focus", help: "The current next skill, why and first action." },
  { key: "showProjects", label: "Projects", help: "Only the projects selected below." },
  { key: "showProof", label: "Proof and artifact links", help: "Selected proof plus repository or demo URLs." },
  { key: "showMilestones", label: "Timeline", help: "Only the timeline events selected below." },
  { key: "showRarity", label: "Illustrative rarity", help: "Always labeled illustrative and not measured." },
];

function toggle<T>(values: T[], value: T, maximum: number) {
  if (values.includes(value)) return values.filter((item) => item !== value);
  return values.length >= maximum ? values : [...values, value];
}

export function PublicProfilePanel({ publication, skills, projects, proofs, intelligence, busy, error, onClose, onReload, onSave, onPublish, onUnpublish, onCreateShare, onRevokeShare, onAddMilestone, onDeleteMilestone }: Props) {
  const [draft, setDraft] = useState<PublicationConfig | null>(null);
  const [shareLabel, setShareLabel] = useState("");
  const [createdShare, setCreatedShare] = useState<PublicationShareCreated | null>(null);
  const [milestoneType, setMilestoneType] = useState<TimelineEventType>("ACTION_COMPLETED");
  const [milestoneTitle, setMilestoneTitle] = useState("");
  const [milestoneDescription, setMilestoneDescription] = useState("");
  const [milestoneDate, setMilestoneDate] = useState(new Date().toISOString().slice(0, 10));

  useEffect(() => { if (publication) setDraft(publication.config); }, [publication]);
  useEffect(() => { if (!publication) void onReload(); }, [onReload, publication]);

  const proofByProject = useMemo(() => new Map(projects.map((project) => [project.projectId, proofs.filter((proof) => proof.projectId === project.projectId)])), [projects, proofs]);
  const publicUrl = publication && typeof window !== "undefined" ? `${window.location.origin}${publication.publicPath}` : publication?.publicPath ?? "";

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!draft) return;
    await onSave({
      slug: draft.slug,
      theme: draft.theme,
      showDisplayName: draft.showDisplayName,
      showSentence: draft.showSentence,
      showSkillLevels: draft.showSkillLevels,
      showIntersections: draft.showIntersections,
      showNextSkill: draft.showNextSkill,
      showProjects: draft.showProjects,
      showProof: draft.showProof,
      showMilestones: draft.showMilestones,
      showRarity: draft.showRarity,
      selectedSkillIds: draft.selectedSkillIds,
      selectedStrategyIds: draft.selectedStrategyIds,
      selectedProjectIds: draft.selectedProjectIds,
      selectedProofIds: draft.selectedProofIds.filter((proofId) => proofs.some((proof) => proof.id === proofId && draft.selectedProjectIds.includes(proof.projectId))),
      selectedMilestoneIds: draft.selectedMilestoneIds,
      version: draft.version,
    });
  };

  return <div className="publication-backdrop" role="presentation">
    <section className="publication-panel" role="dialog" aria-modal="true" aria-labelledby="publication-title">
      <header><div><div className="eyebrow">Private by default</div><h2 id="publication-title">Choose exactly what becomes public</h2><p>Saving updates the private preview. Publishing stores a sanitized snapshot; later private edits automatically withdraw it until you review and publish again.</p></div><button autoFocus className="publication-close" aria-label="Close public profile controls" onClick={onClose}>Close</button></header>

      {!publication || !draft ? <div className="publication-loading" aria-live="polite">Preparing your private publication preview...</div> : <div className="publication-workspace">
        <form className="publication-controls" onSubmit={(event) => void submit(event)}>
          <section><div className="publication-section-title"><span>1</span><div><h3>Address and theme</h3><p>The random default slug does not reveal your name.</p></div></div><label>Public profile address<div className="slug-input"><span>/public/</span><input required minLength={3} maxLength={60} pattern="[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" value={draft.slug} onChange={(event) => setDraft({ ...draft, slug: event.target.value.toLowerCase().replaceAll(/[^a-z0-9-]/g, "") })} /></div></label><fieldset><legend>Theme</legend><div className="theme-options">{(["OBSIDIAN", "PAPER", "SUNRISE"] as PublicTheme[]).map((theme) => <label key={theme} className={`theme-option theme-option-${theme.toLowerCase()}`}><input type="radio" name="theme" checked={draft.theme === theme} onChange={() => setDraft({ ...draft, theme })} /><span>{theme.toLowerCase()}</span></label>)}</div></fieldset></section>

          <section><div className="publication-section-title"><span>2</span><div><h3>Visible fields</h3><p>Everything stays private unless enabled here and included in the preview.</p></div></div><div className="visibility-list">{visibilityFields.map((field) => <label key={field.key}><input type="checkbox" checked={Boolean(draft[field.key])} onChange={(event) => setDraft({ ...draft, [field.key]: event.target.checked })} /><span><strong>{field.label}</strong><small>{field.help}</small></span></label>)}</div></section>

          <section><div className="publication-section-title"><span>3</span><div><h3>Skills</h3><p>Select up to three. Hidden skills never enter the public payload.</p></div></div><div className="selection-list">{skills.map((skill) => <label key={skill.id}><input type="checkbox" checked={draft.selectedSkillIds.includes(skill.id)} onChange={() => setDraft({ ...draft, selectedSkillIds: toggle(draft.selectedSkillIds, skill.id, 3) })} /><span><strong>{skill.name}</strong><small>{(skill.effectiveLevel ?? skill.level ?? "LEARNING").toLowerCase().replaceAll("_", " ")}</small></span></label>)}</div></section>

          {intelligence && <section><div className="publication-section-title"><span>4</span><div><h3>Combination paths</h3><p>Select up to three current canonical strategies.</p></div></div><div className="selection-list">{intelligence.strategies.map((strategy) => <label key={strategy.id}><input type="checkbox" checked={draft.selectedStrategyIds.includes(strategy.id)} onChange={() => setDraft({ ...draft, selectedStrategyIds: toggle(draft.selectedStrategyIds, strategy.id, 3) })} /><span><strong>{strategy.title}</strong><small>{strategy.tradeoff}</small></span></label>)}</div></section>}

          <section><div className="publication-section-title"><span>5</span><div><h3>Projects and proof</h3><p>Artifact URLs appear only when proof visibility is enabled.</p></div></div><div className="selection-list project-selections">{projects.map((project) => { const projectId = project.projectId; if (!projectId) return null; return <div key={projectId}><label><input type="checkbox" checked={draft.selectedProjectIds.includes(projectId)} onChange={() => { const selectedProjectIds = toggle(draft.selectedProjectIds, projectId, 6); setDraft({ ...draft, selectedProjectIds, selectedProofIds: draft.selectedProofIds.filter((proofId) => proofs.some((proof) => proof.id === proofId && selectedProjectIds.includes(proof.projectId))) }); }} /><span><strong>{project.name}</strong><small>{(project.status ?? "IDEA").toLowerCase()}</small></span></label>{draft.selectedProjectIds.includes(projectId) && proofByProject.get(projectId)?.map((proof) => <label className="proof-selection" key={proof.id}><input type="checkbox" checked={draft.selectedProofIds.includes(proof.id)} onChange={() => setDraft({ ...draft, selectedProofIds: toggle(draft.selectedProofIds, proof.id, 12) })} /><span><strong>{proof.title}</strong><small>{proof.type.toLowerCase().replaceAll("_", " ")}</small></span></label>)}</div>; })}</div></section>

          <section><div className="publication-section-title"><span>6</span><div><h3>Timeline</h3><p>Select factual events, or add a clearly user-dated milestone.</p></div></div><div className="selection-list timeline-selections">{publication.timeline.map((event) => <div className="timeline-selection" key={event.id}><label><input type="checkbox" checked={draft.selectedMilestoneIds.includes(event.id)} onChange={() => setDraft({ ...draft, selectedMilestoneIds: toggle(draft.selectedMilestoneIds, event.id, 20) })} /><span><strong>{event.title}</strong><small>{event.occurredOn ?? event.recordedAt.slice(0, 10)} - {event.dateSource === "USER_ENTERED" ? "date entered by you" : "recorded by Skill Stack"}</small></span></label>{event.sourceType === "USER_MILESTONE" && <button type="button" onClick={() => void onDeleteMilestone(event.id)}>Delete</button>}</div>)}</div><div className="milestone-form"><select aria-label="Milestone type" value={milestoneType} onChange={(event) => setMilestoneType(event.target.value as TimelineEventType)}><option value="ACTION_COMPLETED">Completed action</option><option value="PERSONAL_MILESTONE">Personal milestone</option></select><input aria-label="Milestone title" maxLength={180} placeholder="What actually happened?" value={milestoneTitle} onChange={(event) => setMilestoneTitle(event.target.value)} /><input aria-label="Milestone date" type="date" max={new Date().toISOString().slice(0, 10)} value={milestoneDate} onChange={(event) => setMilestoneDate(event.target.value)} /><textarea aria-label="Milestone description" maxLength={1000} placeholder="Optional context without private details" value={milestoneDescription} onChange={(event) => setMilestoneDescription(event.target.value)} /><button type="button" className="secondary-button" disabled={!milestoneTitle || !milestoneDate || busy} onClick={() => void onAddMilestone({ type: milestoneType, title: milestoneTitle, description: milestoneDescription || null, occurredOn: milestoneDate }).then(() => { setMilestoneTitle(""); setMilestoneDescription(""); })}>Add factual milestone</button></div></section>

          <div className="publication-save"><button className="primary-button" disabled={busy}>{busy ? "Saving..." : "Save and refresh private preview"}</button>{error && <p className="inline-error" role="alert">{error}</p>}</div>
        </form>

        <aside className="publication-preview" aria-label="Private public-profile preview"><div className="preview-label"><span>Private preview</span><strong>{publication.config.status === "PUBLISHED" ? "Currently published" : "Not public"}</strong></div><div className={`mini-public-profile mini-theme-${publication.preview.theme.toLowerCase()}`}><div className="eyebrow">Selective Skill Stack</div><h3>{publication.preview.displayName ? `${publication.preview.displayName}'s Skill Stack` : "A Skill Stack in progress"}</h3>{publication.preview.sentence && <p>{publication.preview.sentence}</p>}<div className="mini-skills">{publication.preview.skills.map((skill) => <span key={skill.name}>{skill.name}{skill.level && <small>{skill.level.toLowerCase().replaceAll("_", " ")}</small>}</span>)}</div>{publication.preview.illustrativeRarity && <div className="mini-rarity"><strong>{publication.preview.illustrativeRarity} illustrative</strong><small>{publication.preview.rarityExplanation}</small></div>}{publication.preview.developmentFocus && <div className="mini-focus"><span>Developing next</span><strong>{publication.preview.developmentFocus.name}</strong><p>{publication.preview.developmentFocus.whyNow}</p></div>}{publication.preview.intersections.map((item) => <article key={item.name}><strong>{item.name}</strong><p>{item.explanation}</p></article>)}{publication.preview.projects.map((project) => <article key={project.title}><strong>{project.title}</strong><p>{project.description}</p><small>{project.proof.length} selected proof item{project.proof.length === 1 ? "" : "s"}</small></article>)}{publication.preview.timeline.length > 0 && <ol>{publication.preview.timeline.map((item, index) => <li key={`${item.recordedAt}-${index}`}>{item.title}</li>)}</ol>}<div className="mini-boundary"><strong>Never included</strong><span>Private role, city, goals, imports, rejected ideas, notes, owner IDs or raw provider output.</span></div></div>

          <div className="publication-actions"><button className="primary-button" disabled={busy || publication.preview.skills.length === 0} onClick={() => void onPublish()}>{publication.config.status === "PUBLISHED" ? "Republish reviewed snapshot" : "Publish this snapshot"}</button>{publication.config.status === "PUBLISHED" && <><a href={publication.publicPath} target="_blank" rel="noreferrer">Open public profile</a><button className="secondary-button" disabled={busy} onClick={() => void onUnpublish()}>Unpublish now</button></>}</div>

          {publication.config.status === "PUBLISHED" && <section className="share-controls"><h3>Revocable share links</h3><p>A share token can be revoked independently. The public slug remains available until you unpublish.</p><div><input maxLength={80} aria-label="Share label" placeholder="LinkedIn draft, portfolio..." value={shareLabel} onChange={(event) => setShareLabel(event.target.value)} /><button className="secondary-button" disabled={busy} onClick={() => void onCreateShare(shareLabel).then((created) => { setCreatedShare(created); setShareLabel(""); })}>Create share link</button></div>{createdShare && <div className="created-share" role="status"><span>Copy this link now; the token is not stored in readable form.</span><a href={createdShare.path} target="_blank" rel="noreferrer">{typeof window !== "undefined" ? `${window.location.origin}${createdShare.path}` : createdShare.path}</a><button onClick={() => void navigator.clipboard?.writeText(`${window.location.origin}${createdShare.path}`)}>Copy</button></div>}<ul>{publication.shares.map((share) => <li key={share.id}><span><strong>{share.label ?? "Share link"}</strong><small>token ending {share.tokenHint} - created {share.createdAt.slice(0, 10)}</small></span>{share.revokedAt ? <em>Revoked</em> : <button disabled={busy} onClick={() => void onRevokeShare(share.id)}>Revoke</button>}</li>)}</ul></section>}
          {publication.config.status === "PUBLISHED" && <p className="published-address">Public address: <a href={publicUrl}>{publicUrl}</a></p>}
        </aside>
      </div>}
    </section>
  </div>;
}
