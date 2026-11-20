import { universe } from "@/data/universe";
import type { UniverseData } from "@/lib/types";

/** Deterministically expands the curated universe for rendering benchmarks. */
export function buildSyntheticUniverse(targetCount: number): UniverseData {
  if (targetCount <= universe.nodes.length) {
    const nodes = universe.nodes.slice(0, Math.max(0, targetCount));
    const ids = new Set(nodes.map((node) => node.id));
    return { ...universe, nodes, edges: universe.edges.filter((edge) => ids.has(edge.source) && ids.has(edge.target)) };
  }

  const nodes = [...universe.nodes];
  const edges = [...universe.edges];
  let copy = 1;
  while (nodes.length < targetCount) {
    const offsetX = ((copy % 5) - 2) * 72;
    const offsetY = (Math.floor(copy / 5) + 1) * 58;
    const remaining = targetCount - nodes.length;
    const sourceNodes = universe.nodes.slice(0, remaining);
    const sourceIds = new Set(sourceNodes.map((node) => node.id));
    sourceNodes.forEach((node) => nodes.push({
      ...node,
      id: `${node.id}-synthetic-${copy}`,
      name: `${node.name} · ${copy + 1}`,
      x: node.x + offsetX,
      y: node.y + offsetY,
    }));
    universe.edges.forEach((edge) => {
      if (!sourceIds.has(edge.source) || !sourceIds.has(edge.target)) return;
      edges.push({
        ...edge,
        id: `${edge.id}-synthetic-${copy}`,
        source: `${edge.source}-synthetic-${copy}`,
        target: `${edge.target}-synthetic-${copy}`,
      });
    });
    copy += 1;
  }
  return { ...universe, nodes, edges };
}
