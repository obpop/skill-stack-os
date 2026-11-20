"use client";

import type { IntersectionDefinition, UniverseNode } from "@/lib/types";

type Props = {
  intersection: IntersectionDefinition;
  contributing: UniverseNode[];
  saved: boolean;
  converted: boolean;
  busy?: boolean;
  onSave: () => void;
  onReject: () => void;
  onConvert: () => void;
};

export function DiscoverySheet({ intersection, contributing, saved, converted, busy, onSave, onReject, onConvert }: Props) {
  return (
    <aside className="editorial-sheet discovery-sheet" aria-live="polite" aria-label="Discovered intersection">
      <div className="discovery-index">NEW INTERSECTION / 01</div>
      <div className="intersection-glyph" aria-hidden="true"><i /><i /><i /><span /></div>
      <h2>{intersection.name}</h2>
      <p className="sheet-lede">{intersection.why}</p>
      <div className="contributor-row" aria-label="Contributing concepts">
        {contributing.map((node) => <span key={node.id}>{node.name}</span>)}
      </div>
      <section className="possibility-block lead-possibility">
        <div className="eyebrow">Project</div>
        <h3>{converted ? "Project created in your universe" : intersection.project}</h3>
      </section>
      <div className="possibility-grid">
        <section className="possibility-block">
          <div className="eyebrow">Explore</div>
          <ul>{intersection.explore.map((item) => <li key={item}>{item}</li>)}</ul>
        </section>
        <section className="possibility-block">
          <div className="eyebrow">Learn</div>
          <ul>{intersection.learn.map((item) => <li key={item}>{item}</li>)}</ul>
        </section>
      </div>
      <section className="direction-line">
        <span className="eyebrow">Possible direction</span>
        <strong>{intersection.direction}</strong>
      </section>
      <div className="sheet-actions discovery-actions">
        <button className="primary-button" onClick={onSave} disabled={saved || busy}>{saved ? "Saved to universe" : "Save intersection"}</button>
        <button className="secondary-button" onClick={onConvert} disabled={converted || busy}>{converted ? "Project created" : "Turn into project"}</button>
        <button className="text-button" onClick={onReject} disabled={converted || busy}>Reject</button>
      </div>
    </aside>
  );
}
