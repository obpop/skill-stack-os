"use client";

import { useState, type FormEvent } from "react";
import { domainMeta } from "@/data/universe";
import type { DomainId, NodeType, UniverseNode } from "@/lib/types";

export type ComposerMode = "node" | "edge" | "project" | "proof";

export type ComposerValue = {
  title: string;
  description: string;
  domain: DomainId;
  type: NodeType;
  importance: number;
  depth: number;
  url?: string;
};

type Props = {
  mode: ComposerMode;
  selected: UniverseNode[];
  defaultDomain?: DomainId;
  busy?: boolean;
  onClose: () => void;
  onSubmit: (value: ComposerValue) => Promise<void>;
};

const expandedUniverse = process.env.NEXT_PUBLIC_EXPANDED_UNIVERSE === "true";
const nodeTypes: NodeType[] = expandedUniverse
  ? ["INTEREST", "SKILL", "KNOWLEDGE", "VALUE", "GOAL", "IDEA", "PERSON", "BOOK", "INDUSTRY"]
  : ["SKILL"];

export function UniverseComposer({ mode, selected, defaultDomain = "ideas", busy, onClose, onSubmit }: Props) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [domain, setDomain] = useState<DomainId>(selected[0]?.domain ?? defaultDomain);
  const [type, setType] = useState<NodeType>("SKILL");
  const [importance, setImportance] = useState(7);
  const [depth, setDepth] = useState(5);
  const [url, setUrl] = useState("");

  const heading = mode === "node" ? "Add to your stack" : mode === "edge" ? "Connect skills" : mode === "project" ? "Create a project" : "Attach proof";

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    await onSubmit({ title, description, domain, type, importance, depth, url: url || undefined });
  };

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <form className="composer-panel" role="dialog" aria-modal="true" aria-label={heading} onSubmit={submit} onMouseDown={(event) => event.stopPropagation()}>
        <div className="popover-heading"><div><div className="eyebrow">Shape your Skill Stack</div><h2>{heading}</h2></div><button type="button" onClick={onClose} aria-label="Close">×</button></div>
        {mode === "edge" ? (
          <p className="composer-context"><strong>{selected[0]?.name}</strong><span>relates to</span><strong>{selected[1]?.name}</strong></p>
        ) : mode === "proof" ? (
          <p className="composer-context"><span>Evidence for</span><strong>{selected[0]?.name}</strong></p>
        ) : (
          <>
            <label>Title<input autoFocus required maxLength={180} value={title} onChange={(event) => setTitle(event.target.value)} placeholder={mode === "project" ? "What will you build?" : "What are you curious about?"} /></label>
            <label>Description<textarea maxLength={2000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Why does this belong in your stack?" /></label>
          </>
        )}
        {mode === "node" && <label>Kind<select value={type} onChange={(event) => setType(event.target.value as NodeType)}>{nodeTypes.map((item) => <option key={item}>{item}</option>)}</select></label>}
        {expandedUniverse && (mode === "node" || mode === "project") && <label>World<select value={domain} onChange={(event) => setDomain(event.target.value as DomainId)}>{Object.entries(domainMeta).map(([id, meta]) => <option key={id} value={id}>{meta.label}</option>)}</select></label>}
        {mode === "node" && <div className="composer-sliders"><label>Importance <strong>{importance}</strong><input type="range" min="1" max="10" value={importance} onChange={(event) => setImportance(Number(event.target.value))} /></label><label>Depth <strong>{depth}</strong><input type="range" min="1" max="10" value={depth} onChange={(event) => setDepth(Number(event.target.value))} /></label></div>}
        {mode === "proof" && <><label>Proof title<input autoFocus required maxLength={180} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Repository, launch, essay…" /></label><label>URL (optional)<input type="url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://" /></label><label>Note<textarea value={description} onChange={(event) => setDescription(event.target.value)} /></label></>}
        {(mode === "project" || mode === "edge") && selected.length > 0 && <div className="composer-selection"><span>Connected context</span>{selected.map((node) => <i key={node.id}>{node.name}</i>)}</div>}
        <div className="sheet-actions"><button className="primary-button" disabled={busy || (mode !== "edge" && !title.trim())}>{busy ? "Saving…" : heading}</button><button className="text-button" type="button" onClick={onClose}>Cancel</button></div>
      </form>
    </div>
  );
}
