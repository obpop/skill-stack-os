import { performance } from "node:perf_hooks";
import Graph from "graphology";
import cytoscape from "cytoscape";

const sizes = [100, 500, 1000, 5000];
const rows = [];

function source(count) {
  const nodes = Array.from({ length: count }, (_, index) => ({
    id: `node-${index}`,
    x: Math.cos(index * 2.399963) * Math.sqrt(index),
    y: Math.sin(index * 2.399963) * Math.sqrt(index),
  }));
  const edges = [];
  for (let index = 1; index < count; index += 1) {
    edges.push({ id: `edge-${index}-a`, source: `node-${index}`, target: `node-${Math.floor((index - 1) / 3)}` });
    if (index > 7 && index % 3 === 0) edges.push({ id: `edge-${index}-b`, source: `node-${index}`, target: `node-${index - 7}` });
  }
  return { nodes, edges };
}

function timed(run) {
  const start = performance.now();
  const value = run();
  return { value, ms: Number((performance.now() - start).toFixed(2)) };
}

for (const count of sizes) {
  const data = source(count);
  const graphology = timed(() => {
    const graph = new Graph({ multi: true });
    data.nodes.forEach((node) => graph.addNode(node.id, node));
    data.edges.forEach((edge) => graph.addEdgeWithKey(edge.id, edge.source, edge.target));
    graph.neighbors(`node-${Math.floor(count / 2)}`);
    return graph;
  });
  const cy = timed(() => cytoscape({
    headless: true,
    styleEnabled: false,
    elements: [
      ...data.nodes.map((node) => ({ data: { id: node.id }, position: node })),
      ...data.edges.map((edge) => ({ data: edge })),
    ],
    layout: { name: "preset" },
  }));
  const reactFlowModel = timed(() => ({
    nodes: data.nodes.map((node) => ({ id: node.id, position: { x: node.x, y: node.y }, data: {} })),
    edges: data.edges.map((edge) => ({ id: edge.id, source: edge.source, target: edge.target })),
  }));
  rows.push({ nodes: count, edges: data.edges.length, graphologyModelMs: graphology.ms, cytoscapeHeadlessMs: cy.ms, reactFlowDataMs: reactFlowModel.ms });
  cy.value.destroy();
}

console.table(rows);
console.log(JSON.stringify({ generatedAt: new Date().toISOString(), note: "Model construction only; renderer interaction is tested in /experiments and Playwright.", rows }, null, 2));
