import Graph from "graphology";
import { domainMeta } from "@/data/universe";
import type { NodeType, UniverseData, UniverseNode } from "@/lib/types";

export type GraphNodeAttributes = Omit<UniverseNode, "type"> & {
  nodeType: NodeType;
  type: "circle";
  label: string;
  size: number;
  color: string;
  baseColor: string;
  baseX: number;
  baseY: number;
  forceLabel?: boolean;
  highlighted?: boolean;
  hidden?: boolean;
};

export type GraphEdgeAttributes = {
  size: number;
  color: string;
  baseColor: string;
  strength: number;
  type: string;
  hidden?: boolean;
  zIndex?: number;
};

export function createGraph(data: UniverseData) {
  const graph = new Graph<GraphNodeAttributes, GraphEdgeAttributes>({ multi: false, type: "undirected" });

  data.nodes.forEach((node) => {
    const skillColor = node.level === "TOP_25" ? "#99D0BA" : node.level === "GOOD" ? "#78A694" : "#587C6F";
    const baseColor = node.effectiveLevel === "PROVEN" || node.type === "PROJECT"
      ? "#A9E5CC"
      : node.type === "SKILL" ? skillColor : node.type === "INTEREST" ? "#68808A" : domainMeta[node.domain].color;
    const levelSize = node.type === "SKILL" ? node.effectiveLevel === "PROVEN" ? 1.8 : node.level === "TOP_25" ? 1.2 : node.level === "GOOD" ? .6 : 0 : 0;
    const semanticSize = node.type === "SKILL" ? 5.4 + node.importance * 0.48 + levelSize : node.type === "INTEREST" ? 2.1 + node.importance * 0.18 : node.type === "PROJECT" ? 7.4 : 2.3 + node.importance * 0.44;
    graph.addNode(node.id, {
      ...node,
      nodeType: node.type,
      type: "circle",
      label: node.name,
      size: node.type === "DOMAIN" ? 10 : semanticSize,
      color: baseColor,
      baseColor,
      baseX: node.x,
      baseY: node.y,
      forceLabel: node.type === "SKILL" || node.type === "PROJECT" || node.importance >= 9,
    });
  });

  data.edges.forEach((edge) => {
    if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target) || graph.hasEdge(edge.source, edge.target)) return;
    const baseColor = edge.type === "evidence_for" ? "#2F5A4C" : "#28363B";
    graph.addEdgeWithKey(edge.id, edge.source, edge.target, {
      size: edge.type === "evidence_for" ? 1.15 : 0.3 + edge.strength * 0.55,
      color: baseColor,
      baseColor,
      strength: edge.strength,
      type: edge.type,
    });
  });

  return graph;
}

export function connectedNeighborhood(graph: Graph<GraphNodeAttributes, GraphEdgeAttributes>, ids: Iterable<string>) {
  const result = new Set<string>();
  for (const id of ids) {
    if (!graph.hasNode(id)) continue;
    result.add(id);
    graph.neighbors(id).forEach((neighbor) => result.add(neighbor));
  }
  return result;
}
