import { describe, expect, it } from "vitest";
import { graphToUniverse } from "@/lib/api/adapter";

const node = (id: string, type: string, overrides = {}) => ({
  id,
  type,
  title: id,
  description: null,
  domain: "ideas",
  importance: 5,
  depth: 5,
  status: "ACTIVE",
  provenance: "USER_CREATED",
  proofOfWork: false,
  level: type === "SKILL" ? "LEARNING" : null,
  effectiveLevel: type === "SKILL" ? "LEARNING" : null,
  monetizable: false,
  learnOrOutsource: null,
  wouldDoForFree: false,
  provenByProjectIds: [],
  layout: { x: 0, y: 0 },
  year: 2026,
  version: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  ...overrides,
});

const graph = {
  nodes: [
    node("skill", "SKILL", { effectiveLevel: "PROVEN", provenByProjectIds: ["project-record"] }),
    node("interest", "INTEREST", { wouldDoForFree: true }),
    node("project", "PROJECT"),
    node("legacy", "BOOK"),
  ],
  edges: [
    { id: "visible", source: "skill", target: "project", type: "RELATES_TO", strength: 1, provenance: "USER_CREATED", version: 0 },
    { id: "hidden", source: "skill", target: "legacy", type: "RELATES_TO", strength: 1, provenance: "USER_CREATED", version: 0 },
  ],
  projects: [],
  proofs: [],
  intersections: [],
};

describe("Skill Stack graph adapter", () => {
  it("shows only skills, interests and projects in the default product", () => {
    const result = graphToUniverse(graph as never, false);
    expect(result.nodes.map(({ id }) => id)).toEqual(["skill", "interest", "project"]);
    expect(result.edges.map(({ id }) => id)).toEqual(["visible"]);
  });

  it("preserves project-derived PROVEN evidence separately from the chosen level", () => {
    const skill = graphToUniverse(graph as never, false).nodes[0];
    expect(skill.level).toBe("LEARNING");
    expect(skill.effectiveLevel).toBe("PROVEN");
    expect(skill.provenByProjectIds).toEqual(["project-record"]);
  });

  it("keeps the legacy universe available only behind the explicit flag", () => {
    expect(graphToUniverse(graph as never, true).nodes).toHaveLength(4);
  });
});
