"use client";

import { useEffect, useState, type FormEvent } from "react";
import { domainMeta } from "@/data/universe";
import type { DomainId, NodeStatus, NodeType, ProjectDefinition, ProofDefinition, SkillLevel, UniverseEdge, UniverseNode } from "@/lib/types";

export type NodeEditValue = {
  title: string;
  description: string;
  type: NodeType;
  domain: DomainId;
  importance: number;
  depth: number;
  status: NodeStatus;
  level?: SkillLevel;
  monetizable: boolean;
  learnOrOutsource?: "LEARN" | "OUTSOURCE";
  wouldDoForFree: boolean;
};

export type RelationshipEditValue = {
  type: "RELATES_TO" | "BELONGS_TO";
  strength: number;
};

export type NodeRelationship = { edge: UniverseEdge; neighbor: UniverseNode };

export type ProjectEditValue = {
  title: string;
  description: string;
  status: "IDEA" | "PLANNED" | "BUILDING" | "COMPLETED" | "ARCHIVED";
  startedAt: string;
  completedAt: string;
  repositoryUrl: string;
  liveUrl: string;
};

type Props = {
  node: UniverseNode;
  neighbors: UniverseNode[];
  relationships: NodeRelationship[];
  project?: ProjectDefinition;
  proofs?: ProofDefinition[];
  provenProjects?: ProjectDefinition[];
  selected: boolean;
  onClose: () => void;
  onToggleSelect: () => void;
  onFocusNeighbor: (id: string) => void;
  onUpdate?: (value: NodeEditValue) => Promise<void>;
  onUpdateRelationship?: (edge: UniverseEdge, value: RelationshipEditValue) => Promise<void>;
  onDeleteRelationship?: (edge: UniverseEdge) => Promise<void>;
  onUpdateProject?: (project: ProjectDefinition, value: ProjectEditValue) => Promise<void>;
  onDeleteProof?: (proof: ProofDefinition) => Promise<void>;
  onDelete?: () => Promise<void>;
  onAddProof?: () => void;
};

const expandedUniverse = process.env.NEXT_PUBLIC_EXPANDED_UNIVERSE === "true";
const editableTypes: NodeType[] = expandedUniverse
  ? ["IDENTITY", "INTEREST", "SKILL", "KNOWLEDGE", "VALUE", "GOAL", "IDEA", "PERSON", "BOOK", "INDUSTRY"]
  : ["SKILL"];
const statuses: NodeStatus[] = ["CURIOUS", "EXPLORING", "ACTIVE", "DEVELOPED", "ARCHIVED"];
const projectTransitions: Record<NonNullable<ProjectDefinition["status"]>, NonNullable<ProjectDefinition["status"]>[]> = {
  IDEA: ["PLANNED", "ARCHIVED"],
  PLANNED: ["IDEA", "BUILDING", "ARCHIVED"],
  BUILDING: ["PLANNED", "COMPLETED", "ARCHIVED"],
  COMPLETED: ["BUILDING", "ARCHIVED"],
  ARCHIVED: ["PLANNED"],
};

function editValue(node: UniverseNode): NodeEditValue {
  return {
    title: node.name,
    description: node.description ?? "",
    type: node.type,
    domain: node.domain,
    importance: node.importance,
    depth: node.depth,
    status: node.status ?? "CURIOUS",
    level: node.level,
    monetizable: node.monetizable ?? false,
    learnOrOutsource: node.learnOrOutsource,
    wouldDoForFree: node.wouldDoForFree ?? false,
  };
}

function projectEditValue(project: ProjectDefinition): ProjectEditValue {
  return {
    title: project.name,
    description: project.description,
    status: project.status ?? "IDEA",
    startedAt: project.startedAt ?? "",
    completedAt: project.completedAt ?? "",
    repositoryUrl: project.repositoryUrl ?? "",
    liveUrl: project.liveUrl ?? "",
  };
}

export function NodeSheet({ node, neighbors, relationships, project, proofs = [], provenProjects = [], selected, onClose, onToggleSelect, onFocusNeighbor, onUpdate, onUpdateRelationship, onDeleteRelationship, onUpdateProject, onDeleteProof, onDelete, onAddProof }: Props) {
  const domain = domainMeta[node.domain];
  const [importance, setImportance] = useState(node.importance);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<NodeEditValue>(() => editValue(node));
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [relationshipDraft, setRelationshipDraft] = useState<(RelationshipEditValue & { edgeId: string }) | null>(null);
  const [deletingRelationship, setDeletingRelationship] = useState<string | null>(null);
  const [relationshipError, setRelationshipError] = useState<string | null>(null);
  const [projectDraft, setProjectDraft] = useState<ProjectEditValue | null>(null);
  const [deletingProof, setDeletingProof] = useState<string | null>(null);
  const [proofError, setProofError] = useState<string | null>(null);
  const canEditDetails = Boolean(onUpdate && editableTypes.includes(node.type));

  useEffect(() => {
    setImportance(node.importance);
    setDraft(editValue(node));
    setConfirmDelete(false);
    setEditError(null);
    setRelationshipDraft(null);
    setDeletingRelationship(null);
    setRelationshipError(null);
    setProjectDraft(null);
    setDeletingProof(null);
    setProofError(null);
  }, [node]);

  const save = async (value: NodeEditValue) => {
    if (!onUpdate) return false;
    setSaving(true);
    setEditError(null);
    try {
      await onUpdate(value);
      return true;
    } catch (problem) {
      setEditError(problem instanceof Error ? problem.message : "The node could not be saved.");
      return false;
    } finally {
      setSaving(false);
    }
  };

  const saveImportance = async () => {
    const saved = await save({ ...editValue(node), importance });
    if (!saved) setImportance(node.importance);
  };

  const saveDetails = async (event: FormEvent) => {
    event.preventDefault();
    if (await save({ ...draft, title: draft.title.trim(), description: draft.description.trim() })) setEditing(false);
  };

  const deleteNode = async () => {
    if (!onDelete) return;
    setDeleting(true);
    setEditError(null);
    try {
      await onDelete();
    } catch (problem) {
      setEditError(problem instanceof Error ? problem.message : "The node could not be deleted.");
      setDeleting(false);
    }
  };

  const saveRelationship = async (event: FormEvent, edge: UniverseEdge) => {
    event.preventDefault();
    if (!onUpdateRelationship || !relationshipDraft) return;
    setSaving(true);
    setRelationshipError(null);
    try {
      await onUpdateRelationship(edge, relationshipDraft);
      setRelationshipDraft(null);
    } catch (problem) {
      setRelationshipError(problem instanceof Error ? problem.message : "The relationship could not be saved.");
    } finally {
      setSaving(false);
    }
  };

  const deleteRelationship = async (edge: UniverseEdge) => {
    if (!onDeleteRelationship) return;
    setRelationshipError(null);
    try {
      await onDeleteRelationship(edge);
      setDeletingRelationship(null);
    } catch (problem) {
      setRelationshipError(problem instanceof Error ? problem.message : "The relationship could not be deleted.");
    }
  };

  const saveProject = async (event: FormEvent) => {
    event.preventDefault();
    if (!project || !projectDraft || !onUpdateProject) return;
    setSaving(true);
    setEditError(null);
    try {
      await onUpdateProject(project, { ...projectDraft, title: projectDraft.title.trim(), description: projectDraft.description.trim() });
      setProjectDraft(null);
    } catch (problem) {
      setEditError(problem instanceof Error ? problem.message : "The project could not be saved.");
    } finally {
      setSaving(false);
    }
  };

  const deleteProof = async (proof: ProofDefinition) => {
    if (!onDeleteProof) return;
    setProofError(null);
    try {
      await onDeleteProof(proof);
      setDeletingProof(null);
    } catch (problem) {
      setProofError(problem instanceof Error ? problem.message : "The proof could not be deleted.");
    }
  };

  return (
    <aside className="editorial-sheet node-sheet" aria-label={`${node.name} details`}>
      <button className="sheet-close" onClick={onClose} aria-label="Close details">×</button>
      {projectDraft && project ? (
        <form className="node-editor project-editor" aria-label={`Edit project ${project.name}`} onSubmit={saveProject}>
          <div className="eyebrow">Project lifecycle</div>
          <label>Project title<input autoFocus required maxLength={180} value={projectDraft.title} onChange={(event) => setProjectDraft({ ...projectDraft, title: event.target.value })} /></label>
          <label>Project description<textarea maxLength={2000} value={projectDraft.description} onChange={(event) => setProjectDraft({ ...projectDraft, description: event.target.value })} /></label>
          <label>Project status<select value={projectDraft.status} onChange={(event) => setProjectDraft({ ...projectDraft, status: event.target.value as ProjectEditValue["status"] })}>{[project.status ?? "IDEA", ...projectTransitions[project.status ?? "IDEA"]].map((status) => <option key={status}>{status}</option>)}</select></label>
          <div className="node-editor-grid">
            <label>Started<input type="date" value={projectDraft.startedAt} onChange={(event) => setProjectDraft({ ...projectDraft, startedAt: event.target.value })} /></label>
            <label>Completed<input type="date" value={projectDraft.completedAt} onChange={(event) => setProjectDraft({ ...projectDraft, completedAt: event.target.value })} /></label>
          </div>
          <label>Repository URL<input type="url" maxLength={1000} placeholder="https://" value={projectDraft.repositoryUrl} onChange={(event) => setProjectDraft({ ...projectDraft, repositoryUrl: event.target.value })} /></label>
          <label>Live URL<input type="url" maxLength={1000} placeholder="https://" value={projectDraft.liveUrl} onChange={(event) => setProjectDraft({ ...projectDraft, liveUrl: event.target.value })} /></label>
          {editError && <p className="inline-error" role="alert">{editError}</p>}
          <div className="sheet-actions"><button className="primary-button" disabled={saving || !projectDraft.title.trim()}>{saving ? "Saving…" : "Save project"}</button><button className="text-button" type="button" disabled={saving} onClick={() => { setProjectDraft(null); setEditError(null); }}>Cancel</button></div>
        </form>
      ) : editing ? (
        <form className="node-editor" aria-label={`Edit ${node.name}`} onSubmit={saveDetails}>
          <div className="eyebrow">Edit signal</div>
          <label>Title<input autoFocus required maxLength={180} value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} /></label>
          <label>Description<textarea maxLength={2000} value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
          <div className="node-editor-grid">
            <label>Kind<select value={draft.type} onChange={(event) => setDraft({ ...draft, type: event.target.value as NodeType })}>{editableTypes.map((type) => <option key={type}>{type}</option>)}</select></label>
            {expandedUniverse && <label>World<select value={draft.domain} onChange={(event) => setDraft({ ...draft, domain: event.target.value as DomainId })}>{Object.entries(domainMeta).map(([id, meta]) => <option key={id} value={id}>{meta.label}</option>)}</select></label>}
            <label>Status<select value={draft.status} onChange={(event) => setDraft({ ...draft, status: event.target.value as NodeStatus })}>{statuses.map((status) => <option key={status}>{status}</option>)}</select></label>
          </div>
          {draft.type === "SKILL" && <div className="skill-edit-fields">
            <label>Level<select value={draft.level ?? "LEARNING"} onChange={(event) => setDraft({ ...draft, level: event.target.value as SkillLevel })}><option value="LEARNING">Learning</option><option value="GOOD">Better than most people</option><option value="TOP_25">Top 25% in my field</option></select></label>
            <label>Develop it<select value={draft.learnOrOutsource ?? ""} onChange={(event) => setDraft({ ...draft, learnOrOutsource: event.target.value ? event.target.value as "LEARN" | "OUTSOURCE" : undefined })}><option value="">Not decided</option><option value="LEARN">Learn</option><option value="OUTSOURCE">Outsource</option></select></label>
            <label className="check-row"><input type="checkbox" checked={draft.monetizable} onChange={(event) => setDraft({ ...draft, monetizable: event.target.checked })} /> Someone would pay for this</label>
          </div>}
          {draft.type === "INTEREST" && <label className="check-row"><input type="checkbox" checked={draft.wouldDoForFree} onChange={(event) => setDraft({ ...draft, wouldDoForFree: event.target.checked })} /> I would do this for free</label>}
          <div className="composer-sliders">
            <label>Importance <strong>{draft.importance}</strong><input aria-label="Edit importance" type="range" min="1" max="10" value={draft.importance} onChange={(event) => setDraft({ ...draft, importance: Number(event.target.value) })} /></label>
            <label>Depth <strong>{draft.depth}</strong><input aria-label="Edit depth" type="range" min="1" max="10" value={draft.depth} onChange={(event) => setDraft({ ...draft, depth: Number(event.target.value) })} /></label>
          </div>
          {editError && <p className="inline-error" role="alert">{editError}</p>}
          <div className="sheet-actions">
            <button className="primary-button" disabled={saving || !draft.title.trim()}>{saving ? "Saving…" : "Save changes"}</button>
            <button className="text-button" type="button" disabled={saving} onClick={() => { setDraft(editValue(node)); setEditError(null); setEditing(false); }}>Cancel</button>
          </div>
        </form>
      ) : (
        <>
          <div className="eyebrow" style={{ color: domain.color }}>
            {node.type.replaceAll("_", " ")} · {domain.label}
          </div>
          <h2>{node.name}</h2>
          <p className="sheet-lede">{node.description ?? defaultDescription(node)}</p>
          {node.type === "SKILL" && <div className="skill-level-summary">
            <span className="eyebrow">Skill level</span>
            <strong>{(node.effectiveLevel ?? node.level ?? "LEARNING").replace("TOP_25", "TOP 25%").toLowerCase()}</strong>
            {node.effectiveLevel === "PROVEN" && <small>Project-backed by {provenProjects.length ? provenProjects.map((item) => item.name).join(", ") : `${node.provenByProjectIds?.length ?? 0} linked project${node.provenByProjectIds?.length === 1 ? "" : "s"}`}. This is evidence inside Skill Stack, not an external certification.</small>}
          </div>}
          <div className="evidence-line">
            <span className={node.proofOfWork ? "evidence-mark active" : "evidence-mark"} />
            <span>{node.proofOfWork ? "Evidence attached" : (node.status ?? "CURIOUS").toLowerCase()}</span>
            <span>Depth {node.depth}/10</span>
          </div>
          {(node.createdAt || node.updatedAt) && <dl className="node-history">
            {node.createdAt && <div><dt>Created</dt><dd>{formatTimestamp(node.createdAt)}</dd></div>}
            {node.updatedAt && <div><dt>Updated</dt><dd>{formatTimestamp(node.updatedAt)}</dd></div>}
          </dl>}
          {project && <div className="project-lifecycle"><div><span className="eyebrow">Project status</span><strong>{project.status?.toLowerCase()}</strong></div>{project.startedAt && <small>Started {project.startedAt}</small>}{project.completedAt && <small>Completed {project.completedAt}</small>}{project.repositoryUrl && <a href={project.repositoryUrl} target="_blank" rel="noreferrer">Repository ↗</a>}{project.liveUrl && <a href={project.liveUrl} target="_blank" rel="noreferrer">Live work ↗</a>}</div>}
          {project && proofs.length > 0 && <div className="proof-list"><div className="eyebrow">Proof of work</div>{proofs.map((proof) => <div className="proof-item" key={proof.id}><div><strong>{proof.title}</strong><small>{proof.type.replaceAll("_", " ")}</small></div>{proof.url && <a href={proof.url} target="_blank" rel="noreferrer">Open ↗</a>}{onDeleteProof && deletingProof !== proof.id && <button className="text-button danger-button" aria-label={`Remove proof ${proof.title}`} onClick={() => setDeletingProof(proof.id)}>Remove</button>}{deletingProof === proof.id && <div className="proof-delete" role="alertdialog" aria-label={`Remove proof ${proof.title}`}><span>Remove this evidence?</span><button className="text-button danger-button" onClick={() => void deleteProof(proof)}>Confirm remove</button><button className="text-button" onClick={() => setDeletingProof(null)}>Cancel</button></div>}</div>)}{proofError && <p className="inline-error" role="alert">{proofError}</p>}</div>}
          {onUpdate && <div className="importance-editor"><div className="eyebrow">Manual importance</div><div><input aria-label="Importance" type="range" min="1" max="10" value={importance} onChange={(event) => setImportance(Number(event.target.value))} /><strong>{importance}/10</strong><button className="text-button" disabled={saving || importance === node.importance} onClick={() => void saveImportance()}>{saving ? "Saving…" : "Apply"}</button></div>{editError && <p className="inline-error" role="alert">{editError}</p>}</div>}
          <div className="sheet-section">
            <div className="eyebrow">Immediate network</div>
            <div className="neighbor-list">
              {relationships.slice(0, 8).map(({ edge, neighbor }) => {
                const editable = edge.type === "relates_to" || edge.type === "belongs_to";
                const editingRelationship = relationshipDraft?.edgeId === edge.id;
                return <div className="relationship-item" key={edge.id}>
                  <button className="relationship-focus" onClick={() => onFocusNeighbor(neighbor.id)}>
                    <span className="neighbor-dot" style={{ background: domainMeta[neighbor.domain].color }} />
                    <span>{neighbor.name}<small>{edge.type.replaceAll("_", " ")} · {Math.round(edge.strength * 100)}%</small></span>
                  </button>
                  {editable && onUpdateRelationship && !editingRelationship && <button className="relationship-action" aria-label={`Edit relationship to ${neighbor.name}`} onClick={() => { setRelationshipError(null); setDeletingRelationship(null); setRelationshipDraft({ edgeId: edge.id, type: edge.type === "belongs_to" ? "BELONGS_TO" : "RELATES_TO", strength: edge.strength }); }}>Edit</button>}
                  {editable && onDeleteRelationship && !editingRelationship && <button className="relationship-action danger-button" aria-label={`Remove relationship to ${neighbor.name}`} onClick={() => { setRelationshipError(null); setDeletingRelationship(edge.id); }}>Remove</button>}
                  {editingRelationship && <form className="relationship-editor" aria-label={`Edit relationship to ${neighbor.name}`} onSubmit={(event) => void saveRelationship(event, edge)}>
                    <label>Relationship<select aria-label="Relationship type" value={relationshipDraft.type} onChange={(event) => setRelationshipDraft({ ...relationshipDraft, type: event.target.value as RelationshipEditValue["type"] })}><option value="RELATES_TO">Relates to</option><option value="BELONGS_TO">Belongs to</option></select></label>
                    <label>Strength <strong>{Math.round(relationshipDraft.strength * 100)}%</strong><input aria-label="Relationship strength" type="range" min="0.1" max="1" step="0.05" value={relationshipDraft.strength} onChange={(event) => setRelationshipDraft({ ...relationshipDraft, strength: Number(event.target.value) })} /></label>
                    <div><button className="text-button" disabled={saving}>{saving ? "Saving…" : "Save relationship"}</button><button className="text-button" type="button" disabled={saving} onClick={() => setRelationshipDraft(null)}>Cancel</button></div>
                  </form>}
                  {deletingRelationship === edge.id && <div className="relationship-delete" role="alertdialog" aria-label={`Remove relationship to ${neighbor.name}`}><span>Remove this connection?</span><button className="text-button danger-button" onClick={() => void deleteRelationship(edge)}>Confirm remove</button><button className="text-button" onClick={() => setDeletingRelationship(null)}>Cancel</button></div>}
                </div>;
              })}
              {relationshipError && <p className="inline-error" role="alert">{relationshipError}</p>}
            </div>
          </div>
          <div className="sheet-actions">
            {(expandedUniverse || node.type === "SKILL") && <button className={selected ? "secondary-button selected" : "primary-button"} onClick={onToggleSelect}>
              {selected ? "Remove from selection" : "Add to intersection"}
            </button>}
            {canEditDetails && <button className="secondary-button" onClick={() => { setDraft(editValue(node)); setEditError(null); setEditing(true); }}>Edit signal</button>}
            {project && onUpdateProject && <button className="secondary-button" onClick={() => { setProjectDraft(projectEditValue(project)); setEditError(null); }}>Edit project</button>}
            {onAddProof && <button className="secondary-button" onClick={onAddProof}>Attach proof</button>}
            {onDelete && !confirmDelete && <button className="text-button danger-button" onClick={() => setConfirmDelete(true)}>Delete</button>}
          </div>
          {confirmDelete && <div className="delete-confirmation" role="alertdialog" aria-label={`Delete ${node.name}`}><strong>Remove “{node.name}”?</strong><p>This also removes {neighbors.length === 1 ? "its relationship" : `its ${neighbors.length} relationships`} from your Skill Stack. This cannot be undone.</p><div className="sheet-actions"><button className="secondary-button danger-confirm" disabled={deleting} onClick={() => void deleteNode()}>{deleting ? "Deleting…" : "Confirm delete"}</button><button className="text-button" disabled={deleting} onClick={() => { setConfirmDelete(false); setEditError(null); }}>Cancel</button></div>{editError && <p className="inline-error" role="alert">{editError}</p>}</div>}
        </>
      )}
    </aside>
  );
}

function defaultDescription(node: UniverseNode) {
  if (node.type === "DOMAIN") return `A major world in this personal universe: ${node.name}.`;
  if (node.type === "PROJECT") return "A project connected to practiced skills and inspectable work.";
  return `${node.name} is part of the ${domainMeta[node.domain].label} neighborhood and connects to the surrounding knowledge graph.`;
}

function formatTimestamp(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
