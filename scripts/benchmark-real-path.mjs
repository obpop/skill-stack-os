import { performance } from "node:perf_hooks";
import Graph from "graphology";

const apiBase = process.env.SKILL_STACK_API_URL ?? "http://localhost:8080/api/v1";
const iterations = Number(process.env.SKILL_STACK_BENCHMARK_ITERATIONS ?? 7);

const loginResponse = await fetch(`${apiBase}/auth/dev-login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ displayName: "Performance baseline", demo: true }),
});
if (!loginResponse.ok) throw new Error(`Demo login failed (${loginResponse.status})`);
const { accessToken } = await loginResponse.json();

const rows = [];
for (let iteration = 0; iteration < iterations; iteration += 1) {
  const requestStart = performance.now();
  const response = await fetch(`${apiBase}/graph`, { headers: { Authorization: `Bearer ${accessToken}` } });
  const payload = await response.text();
  const responseMs = performance.now() - requestStart;
  if (!response.ok) throw new Error(`Graph request failed (${response.status})`);

  const parseStart = performance.now();
  const data = JSON.parse(payload);
  const parseMs = performance.now() - parseStart;

  const graphStart = performance.now();
  const graph = new Graph({ multi: true });
  data.nodes.forEach((node) => graph.addNode(node.id, node));
  data.edges.forEach((edge) => graph.addEdgeWithKey(edge.id, edge.source, edge.target, edge));
  const graphologyMs = performance.now() - graphStart;

  rows.push({
    iteration: iteration + 1,
    nodes: graph.order,
    edges: graph.size,
    payloadKiB: Number((Buffer.byteLength(payload) / 1024).toFixed(1)),
    responseMs: Number(responseMs.toFixed(2)),
    parseMs: Number(parseMs.toFixed(2)),
    graphologyMs: Number(graphologyMs.toFixed(2)),
    totalClientPathMs: Number((responseMs + parseMs + graphologyMs).toFixed(2)),
  });
}

const sorted = (field) => rows.map((row) => row[field]).sort((a, b) => a - b);
const median = (field) => {
  const values = sorted(field);
  return values[Math.floor(values.length / 2)];
};

console.table(rows);
console.log(JSON.stringify({
  generatedAt: new Date().toISOString(),
  apiBase,
  iterations,
  median: {
    responseMs: median("responseMs"),
    parseMs: median("parseMs"),
    graphologyMs: median("graphologyMs"),
    totalClientPathMs: median("totalClientPathMs"),
  },
  note: "Measures PostgreSQL/JPA/serialization/network payload, JSON parsing, and Graphology construction for the 164-node demo. Playwright separately verifies Sigma canvas readiness.",
}, null, 2));
