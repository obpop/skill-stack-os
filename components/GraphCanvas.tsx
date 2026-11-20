"use client";

import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
} from "react";
import type Sigma from "sigma";
import type { Attributes } from "graphology-types";
import { createGraph, type GraphEdgeAttributes, type GraphNodeAttributes } from "@/lib/graph";
import { domainMeta } from "@/data/universe";
import type {
  DiscoveryPhase,
  DomainId,
  IntersectionDefinition,
  NodeStatus,
  UniverseData,
} from "@/lib/types";

export type GraphHandle = {
  focusNode: (id: string) => void;
  reset: () => void;
  zoomIn: () => void;
  zoomOut: () => void;
  exportCanvas: () => string | null;
};

type Props = {
  data: UniverseData;
  selected: Set<string>;
  hovered: string | null;
  focused: string | null;
  searchResult: string | null;
  relevant: Set<string>;
  activeDomains: Set<DomainId>;
  activeTypes: Set<string>;
  activeStatuses: Set<NodeStatus>;
  minimumImportance: number;
  proofFilter: "any" | "with" | "without";
  year: number;
  phase: DiscoveryPhase;
  intersection: IntersectionDefinition | null;
  intersectionSaved: boolean;
  onNodeClick: (id: string) => void;
  onHover: (id: string | null) => void;
  onStageClick: () => void;
};

type VisualState = Omit<Props, "data" | "onNodeClick" | "onHover" | "onStageClick">;

const DIM_NODE = "#1B201F";
const DIM_EDGE = "#1A1F1E";
const ACTIVE_EDGE = "#A9E5CC";
const GOLD = "#7FDDE0";

export const GraphCanvas = forwardRef<GraphHandle, Props>(function GraphCanvas(props, ref) {
  const containerRef = useRef<HTMLDivElement>(null);
  const rendererRef = useRef<Sigma<GraphNodeAttributes, GraphEdgeAttributes> | null>(null);
  const graph = useMemo(() => createGraph(props.data), [props.data]);
  const [keyboardNode, setKeyboardNode] = useState<string | null>(null);
  const [renderUnavailable, setRenderUnavailable] = useState(false);
  const visualRef = useRef<VisualState>(props);
  visualRef.current = props;

  const callbacksRef = useRef({
    onNodeClick: props.onNodeClick,
    onHover: props.onHover,
    onStageClick: props.onStageClick,
  });
  callbacksRef.current = props;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    setRenderUnavailable(false);
    let renderer: Sigma<GraphNodeAttributes, GraphEdgeAttributes> | null = null;
    let disposed = false;
    void import("sigma").then(({ default: SigmaRuntime }) => {
      if (disposed) return;
      renderer = new SigmaRuntime<GraphNodeAttributes, GraphEdgeAttributes>(graph, container, {
        renderEdgeLabels: false,
        enableEdgeEvents: false,
        labelFont: "var(--font-interface)",
        labelWeight: "500",
        labelColor: { color: "#C6CBC5" },
        labelSize: 12,
        labelDensity: 0.75,
        labelGridCellSize: 130,
        labelRenderedSizeThreshold: 7.5,
        defaultNodeColor: "#78909A",
        defaultEdgeColor: "#2A302E",
        defaultEdgeType: "line",
        minCameraRatio: 0.12,
        maxCameraRatio: 5.5,
        zIndex: true,
        hideEdgesOnMove: true,
        nodeReducer: (node, data) => reduceNode(node, data, graph, visualRef.current),
        edgeReducer: (edge, data) => reduceEdge(edge, data, graph, visualRef.current),
      });

      renderer.on("clickNode", ({ node }) => callbacksRef.current.onNodeClick(node));
      renderer.on("enterNode", ({ node }) => callbacksRef.current.onHover(node));
      renderer.on("leaveNode", () => callbacksRef.current.onHover(null));
      renderer.on("clickStage", () => callbacksRef.current.onStageClick());
      rendererRef.current = renderer;
    }).catch(() => {
      if (!disposed) setRenderUnavailable(true);
    });

    return () => {
      disposed = true;
      renderer?.kill();
      rendererRef.current = null;
    };
  }, [graph]);

  useEffect(() => {
    rendererRef.current?.refresh();
  }, [props.selected, props.hovered, props.focused, props.searchResult, props.relevant, props.activeDomains, props.activeTypes, props.activeStatuses, props.minimumImportance, props.proofFilter, props.year, props.phase]);

  useEffect(() => {
    if (props.phase !== "converging" || props.selected.size < 2) return;
    const ids = [...props.selected].filter((id) => graph.hasNode(id));
    const centroid = ids.reduce(
      (point, id) => ({
        x: point.x + graph.getNodeAttribute(id, "baseX") / ids.length,
        y: point.y + graph.getNodeAttribute(id, "baseY") / ids.length,
      }),
      { x: 0, y: 0 },
    );
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const started = performance.now();
    const duration = reduceMotion ? 1 : 620;
    let frame = 0;
    const tick = (now: number) => {
      const progress = Math.min(1, (now - started) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      ids.forEach((id, index) => {
        const baseX = graph.getNodeAttribute(id, "baseX");
        const baseY = graph.getNodeAttribute(id, "baseY");
        const angle = (index / ids.length) * Math.PI * 2;
        const targetX = centroid.x + Math.cos(angle) * 3.8;
        const targetY = centroid.y + Math.sin(angle) * 3.8;
        graph.mergeNodeAttributes(id, {
          x: baseX + (targetX - baseX) * eased,
          y: baseY + (targetY - baseY) * eased,
        });
      });
      rendererRef.current?.refresh();
      if (progress < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    rendererRef.current?.getCamera().animate(
      { x: 0.5, y: 0.5, ratio: 0.6 },
      { duration, easing: "quadraticInOut" },
    );
    return () => cancelAnimationFrame(frame);
  }, [graph, props.phase, props.selected]);

  useEffect(() => {
    const definition = props.intersection;
    if (!definition || props.phase !== "revealed") return;
    const nodeId = definition.id;
    const ids = [...props.selected].filter((id) => graph.hasNode(id));
    const center = ids.reduce(
      (point, id) => ({
        x: point.x + graph.getNodeAttribute(id, "x") / Math.max(1, ids.length),
        y: point.y + graph.getNodeAttribute(id, "y") / Math.max(1, ids.length),
      }),
      { x: 0, y: 0 },
    );
    if (!graph.hasNode(nodeId)) {
      const domain = (ids[0] && graph.getNodeAttribute(ids[0], "domain")) || "ideas";
      graph.addNode(nodeId, {
        id: nodeId,
        name: definition.name,
        label: definition.name,
        nodeType: "INTERSECTION",
        type: "circle",
        domain,
        importance: 10,
        depth: 10,
        activity: 10,
        year: 2026,
        description: definition.why,
        x: center.x,
        y: center.y,
        baseX: center.x,
        baseY: center.y,
        size: 12,
        color: GOLD,
        baseColor: GOLD,
        forceLabel: true,
        highlighted: true,
      });
      ids.forEach((id, index) => {
        graph.addEdgeWithKey(`discovery-${definition.id}-${index}`, nodeId, id, {
          size: 1.8,
          color: GOLD,
          baseColor: GOLD,
          strength: 1,
          type: "enabled",
          zIndex: 4,
        });
      });
    }
    rendererRef.current?.refresh();
  }, [graph, props.intersection, props.phase, props.selected]);

  useEffect(() => {
    if (props.phase !== "idle") return;
    graph.forEachNode((node, attributes) => {
      if (attributes.nodeType !== "INTERSECTION" || props.intersectionSaved) return;
      graph.dropNode(node);
    });
    graph.forEachNode((node, attributes) => {
      if (attributes.nodeType === "INTERSECTION") return;
      graph.mergeNodeAttributes(node, { x: attributes.baseX, y: attributes.baseY });
    });
    rendererRef.current?.refresh();
  }, [graph, props.phase, props.intersectionSaved]);

  const focusRendererNode = (id: string) => {
    const renderer = rendererRef.current;
    if (!renderer || !graph.hasNode(id)) return;
    const position = renderer.getNodeDisplayData(id);
    if (!position) return;
    renderer.getCamera().animate(
      { ...renderer.getCamera().getState(), x: position.x, y: position.y, ratio: 0.38 },
      { duration: 680, easing: "quadraticInOut" },
    );
  };

  useImperativeHandle(ref, () => ({
    focusNode: (id: string) => {
      focusRendererNode(id);
    },
    reset: () => {
      graph.forEachNode((node, attributes) => {
        if (attributes.nodeType !== "INTERSECTION") {
          graph.mergeNodeAttributes(node, { x: attributes.baseX, y: attributes.baseY });
        }
      });
      rendererRef.current?.getCamera().animatedReset({ duration: 700 });
      rendererRef.current?.refresh();
    },
    zoomIn: () => rendererRef.current?.getCamera().animatedZoom({ duration: 260 }),
    zoomOut: () => rendererRef.current?.getCamera().animatedUnzoom({ duration: 260 }),
    exportCanvas: () => {
      const canvas = containerRef.current?.querySelector<HTMLCanvasElement>("canvas.sigma-nodes");
      return canvas?.toDataURL("image/png") ?? null;
    },
  }));

  const keyboardNodes = graph.nodes().filter((id) => graph.getNodeAttribute(id, "nodeType") !== "INTERSECTION")
    .sort((left, right) => graph.getNodeAttribute(left, "name").localeCompare(graph.getNodeAttribute(right, "name")));
  const handleKeyboard = (event: KeyboardEvent<HTMLDivElement>) => {
    if ((event.key === "Enter" || event.key === " ") && keyboardNode) {
      event.preventDefault();
      callbacksRef.current.onNodeClick(keyboardNode);
      return;
    }
    if (!["ArrowRight", "ArrowDown", "ArrowLeft", "ArrowUp", "Home", "End"].includes(event.key) || keyboardNodes.length === 0) return;
    event.preventDefault();
    const current = Math.max(0, keyboardNodes.indexOf(keyboardNode ?? ""));
    const nextIndex = event.key === "Home" ? 0
      : event.key === "End" ? keyboardNodes.length - 1
        : event.key === "ArrowRight" || event.key === "ArrowDown" ? (current + 1) % keyboardNodes.length
          : (current - 1 + keyboardNodes.length) % keyboardNodes.length;
    const next = keyboardNodes[nextIndex];
    setKeyboardNode(next);
    callbacksRef.current.onHover(next);
    focusRendererNode(next);
  };

  if (renderUnavailable) return (
    <div className="graph-canvas graph-unavailable" role="region" aria-label="Skill map unavailable">
      <p>The visual map is unavailable on this device. Your written Skill Stack results and the structured list remain available.</p>
    </div>
  );

  return <>
    <div
      className="graph-canvas"
      ref={containerRef}
      role="application"
      tabIndex={0}
      aria-label="Interactive Skill Stack map. Use arrow keys to browse nodes and Enter to open the focused node."
      onKeyDown={handleKeyboard}
      onFocus={() => {
        if (keyboardNode || keyboardNodes.length === 0) return;
        setKeyboardNode(keyboardNodes[0]);
        callbacksRef.current.onHover(keyboardNodes[0]);
      }}
      onBlur={() => callbacksRef.current.onHover(null)}
    />
    <span className="sr-only" role="status" aria-live="polite">{keyboardNode ? `${graph.getNodeAttribute(keyboardNode, "name")}, ${graph.getNodeAttribute(keyboardNode, "nodeType").toLowerCase()}` : ""}</span>
  </>;
});

function reduceNode(
  node: string,
  data: GraphNodeAttributes,
  graph: ReturnType<typeof createGraph>,
  state: VisualState,
): GraphNodeAttributes {
  const visibleByDomain = state.activeDomains.size === 0 || state.activeDomains.has(data.domain);
  const visibleByType = state.activeTypes.size === 0 || state.activeTypes.has(data.nodeType);
  if (!visibleByDomain || !visibleByType || !visibleByExtendedFilters(data, state) || data.year > state.year) return { ...data, hidden: true };

  const intentNode = state.hovered ?? state.focused ?? state.searchResult;
  const selected = state.selected.has(node);
  const neighborOfIntent = intentNode ? graph.hasNode(intentNode) && graph.areNeighbors(node, intentNode) : false;
  const discoveryActive = state.phase !== "idle";
  const relevant = state.relevant.has(node) || selected || data.nodeType === "INTERSECTION";
  const shouldDim = (intentNode && node !== intentNode && !neighborOfIntent && !selected) || (discoveryActive && !relevant);

  let color = data.baseColor;
  let size = data.size;
  let forceLabel = data.forceLabel;
  let zIndex = 1;

  if (shouldDim) {
    color = DIM_NODE;
    size *= 0.72;
    forceLabel = false;
  }
  if (neighborOfIntent || (discoveryActive && relevant)) {
    color = data.baseColor;
    size *= 1.08;
    forceLabel = data.importance >= 7;
    zIndex = 2;
  }
  if (state.searchResult === node) {
    color = "#F0EEE6";
    size *= 1.45;
    forceLabel = true;
    zIndex = 5;
  }
  if (selected) {
    color = GOLD;
    size *= 1.55;
    forceLabel = true;
    zIndex = 6;
  }
  if (state.hovered === node || state.focused === node) {
    color = "#F3F0E7";
    size *= 1.36;
    forceLabel = true;
    zIndex = 7;
  }
  if (data.nodeType === "PROJECT" && !shouldDim) {
    color = "#A9E5CC";
    forceLabel = true;
  }
  if (data.nodeType === "INTERSECTION") {
    color = state.intersectionSaved ? "#A7C98D" : GOLD;
    size = state.intersectionSaved ? 10 : 13;
    forceLabel = true;
    zIndex = 10;
  }

  return { ...data, color, size, forceLabel, zIndex, hidden: false } as GraphNodeAttributes;
}

function reduceEdge(
  edge: string,
  data: GraphEdgeAttributes,
  graph: ReturnType<typeof createGraph>,
  state: VisualState,
): Partial<GraphEdgeAttributes & Attributes> {
  const [source, target] = graph.extremities(edge);
  const sourceData = graph.getNodeAttributes(source);
  const targetData = graph.getNodeAttributes(target);
  const visible =
    sourceData.year <= state.year &&
    targetData.year <= state.year &&
    visibleByExtendedFilters(sourceData, state) &&
    visibleByExtendedFilters(targetData, state) &&
    (state.activeDomains.size === 0 || state.activeDomains.has(sourceData.domain) || state.activeDomains.has(targetData.domain)) &&
    (state.activeTypes.size === 0 || state.activeTypes.has(sourceData.nodeType) || state.activeTypes.has(targetData.nodeType));
  if (!visible) return { hidden: true };

  const intentNode = state.hovered ?? state.focused ?? state.searchResult;
  const selectedEdge = state.selected.has(source) && state.selected.has(target);
  const touchesSelected = state.selected.has(source) || state.selected.has(target);
  const touchesIntent = intentNode === source || intentNode === target;
  const bothRelevant = state.relevant.has(source) && state.relevant.has(target);
  const discoveryActive = state.phase !== "idle";

  if (data.type === "enabled") return { color: GOLD, size: 2.1, zIndex: 8, hidden: false };
  if (touchesIntent) return { color: ACTIVE_EDGE, size: Math.max(1.2, data.size * 1.9), zIndex: 5, hidden: false };
  if (selectedEdge) return { color: GOLD, size: 1.8, zIndex: 6, hidden: false };
  if (discoveryActive && bothRelevant) {
    return { color: touchesSelected ? GOLD : "#4F6A60", size: touchesSelected ? 1.7 : 0.9, zIndex: 4, hidden: false };
  }
  if ((intentNode && !touchesIntent) || (discoveryActive && !bothRelevant)) {
    return { color: DIM_EDGE, size: 0.2, zIndex: 0, hidden: false };
  }
  return { color: data.baseColor, size: data.size, zIndex: 1, hidden: false };
}

function visibleByExtendedFilters(data: GraphNodeAttributes, state: VisualState) {
  const visibleByStatus = state.activeStatuses.size === 0 || (data.status !== undefined && state.activeStatuses.has(data.status));
  const visibleByImportance = data.importance >= state.minimumImportance;
  const visibleByProof = state.proofFilter === "any" || (state.proofFilter === "with" ? data.proofOfWork === true : data.proofOfWork !== true);
  return visibleByStatus && visibleByImportance && visibleByProof;
}

export function domainColor(domain: DomainId) {
  return domainMeta[domain].color;
}
