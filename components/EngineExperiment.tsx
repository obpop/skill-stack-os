"use client";

import { useEffect, useMemo, useRef } from "react";
import cytoscape from "cytoscape";
import { Background, ReactFlow } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { GraphCanvas } from "@/components/GraphCanvas";
import { domainMeta } from "@/data/universe";
import { buildSyntheticUniverse } from "@/lib/synthetic";

export function EngineExperiment({ engine, count }: { engine: string; count: number }) {
  const data = useMemo(() => buildSyntheticUniverse(count), [count]);
  return (
    <main className="experiment-shell">
      <header>
        <div><span className="eyebrow">Controlled graph experiment</span><h1>{engine} / {data.nodes.length} nodes</h1></div>
        <nav>
          {(["sigma", "cytoscape", "reactflow"] as const).map((name) => <a className={engine === name ? "active" : ""} key={name} href={`/experiments?engine=${name}&count=${count}`}>{name}</a>)}
        </nav>
        <div className="experiment-counts">{[100, 500, 1000, 5000].map((size) => <a key={size} href={`/experiments?engine=${engine}&count=${size}`}>{size}</a>)}</div>
      </header>
      <section className="experiment-stage">
        {engine === "cytoscape" ? <CytoscapeExperiment data={data} /> : engine === "reactflow" ? <ReactFlowExperiment data={data} /> : (
          <GraphCanvas
            data={data}
            selected={new Set()}
            hovered={null}
            focused={null}
            searchResult={null}
            relevant={new Set()}
            activeDomains={new Set()}
            activeTypes={new Set()}
            activeStatuses={new Set()}
            minimumImportance={1}
            proofFilter="any"
            year={2026}
            phase="idle"
            intersection={null}
            intersectionSaved={false}
            onNodeClick={() => undefined}
            onHover={() => undefined}
            onStageClick={() => undefined}
          />
        )}
      </section>
    </main>
  );
}

function CytoscapeExperiment({ data }: { data: ReturnType<typeof buildSyntheticUniverse> }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!ref.current) return;
    const instance = cytoscape({
      container: ref.current,
      elements: [
        ...data.nodes.map((node) => ({ data: { id: node.id, label: node.importance >= 9 ? node.name : "", color: domainMeta[node.domain].color, size: 4 + node.importance }, position: { x: node.x * 11, y: node.y * 11 } })),
        ...data.edges.map((edge) => ({ data: { id: edge.id, source: edge.source, target: edge.target, strength: edge.strength } })),
      ],
      layout: { name: "preset", fit: true, padding: 50 },
      pixelRatio: 1,
      hideEdgesOnViewport: true,
      style: [
        { selector: "node", style: { "background-color": "data(color)", width: "data(size)", height: "data(size)", label: "data(label)", color: "#aeb8b5", "font-size": 7, "text-valign": "bottom", "text-margin-y": 5 } },
        { selector: "edge", style: { width: 0.45, "line-color": "#29383d", opacity: 0.7, "curve-style": "straight" } },
        { selector: ":selected", style: { "background-color": "#e4ca8b", "border-width": 2, "border-color": "#fff0bd" } },
      ],
    });
    return () => instance.destroy();
  }, [data]);
  return <div ref={ref} className="experiment-renderer" />;
}

function ReactFlowExperiment({ data }: { data: ReturnType<typeof buildSyntheticUniverse> }) {
  const nodes = useMemo(() => data.nodes.map((node) => ({
    id: node.id,
    position: { x: node.x * 15, y: node.y * 15 },
    data: { label: node.importance >= 9 ? node.name : "" },
    style: {
      width: 4 + node.importance * 1.2,
      height: 4 + node.importance * 1.2,
      minWidth: 0,
      padding: 0,
      borderRadius: "50%",
      border: 0,
      background: domainMeta[node.domain].color,
      color: "#aeb8b5",
      fontSize: 7,
      textIndent: 17,
      whiteSpace: "nowrap" as const,
    },
  })), [data.nodes]);
  const edges = useMemo(() => data.edges.map((edge) => ({ id: edge.id, source: edge.source, target: edge.target, style: { stroke: "#29383d", strokeWidth: 0.6 } })), [data.edges]);
  return <div className="experiment-renderer"><ReactFlow nodes={nodes} edges={edges} fitView minZoom={0.03} maxZoom={4} nodesDraggable={false} onlyRenderVisibleElements><Background color="#182225" gap={40} size={1} /></ReactFlow></div>;
}
