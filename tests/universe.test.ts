import { describe, expect, it } from "vitest";
import { buildUniverse, chooseIntersection, findNodeByName, universe } from "@/data/universe";
import { createGraph } from "@/lib/graph";
import { buildSyntheticUniverse } from "@/lib/synthetic";

describe("Skill Stack universe", () => {
  it("contains a realistic, internally valid dataset", () => {
    expect(universe.nodes.length).toBeGreaterThanOrEqual(150);
    expect(universe.edges.length).toBeGreaterThan(universe.nodes.length);
    expect(new Set(universe.nodes.map((node) => node.id)).size).toBe(universe.nodes.length);
    const ids = new Set(universe.nodes.map((node) => node.id));
    universe.edges.forEach((edge) => {
      expect(ids.has(edge.source)).toBe(true);
      expect(ids.has(edge.target)).toBe(true);
      expect(edge.strength).toBeGreaterThan(0);
      expect(edge.strength).toBeLessThanOrEqual(1);
    });
  });

  it("finds the signature Financial Intelligence Systems intersection", () => {
    const selection = ["Backend development with Spring Boot", "AI", "Investing", "Economics"].map((name) => findNodeByName(name)!).filter(Boolean);
    const result = chooseIntersection(selection);
    expect(result.name).toBe("Financial Intelligence Systems");
    expect(result.project).toContain("event-driven investment intelligence engine");
  });

  it("builds usable small and empty states", () => {
    expect(buildUniverse(0).nodes).toHaveLength(0);
    expect(buildUniverse(3).nodes).toHaveLength(3);
    expect(buildUniverse(5).nodes).toHaveLength(5);
    expect(buildUniverse(10).nodes).toHaveLength(10);
    expect(buildUniverse(20).nodes).toHaveLength(20);
    expect(() => createGraph(buildUniverse(100))).not.toThrow();
  });

  it("creates deterministic large performance datasets", () => {
    for (const count of [500, 1000, 5000]) {
      const data = buildSyntheticUniverse(count);
      expect(data.nodes).toHaveLength(count);
      expect(new Set(data.nodes.map((node) => node.id)).size).toBe(count);
    }
  });
});
