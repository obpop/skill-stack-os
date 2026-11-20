import { intersections as discoveryDefinitions } from "@/data/universe";
import type { DomainId, NodeStatus, NodeType, UniverseData } from "@/lib/types";
import type { ApiGraph, ApiNode } from "@/lib/api/types";

const domains = new Set<DomainId>(["engineering", "ai", "business", "economics", "ideas", "culture", "world", "health", "communication"]);

export function apiNodeToUniverse(node: ApiNode) {
  return {
    id: node.id,
    type: node.type as NodeType,
    name: node.title,
    domain: domains.has(node.domain as DomainId) ? node.domain as DomainId : "ideas" as DomainId,
    importance: node.importance,
    depth: node.depth,
    activity: node.status === "ACTIVE" ? 9 : node.status === "DEVELOPED" ? 7 : 4,
    proofOfWork: node.proofOfWork,
    level: node.level ?? undefined,
    effectiveLevel: node.effectiveLevel ?? undefined,
    monetizable: node.monetizable,
    learnOrOutsource: node.learnOrOutsource ?? undefined,
    wouldDoForFree: node.wouldDoForFree,
    provenByProjectIds: node.provenByProjectIds,
    description: node.description ?? undefined,
    status: node.status as NodeStatus,
    createdAt: node.createdAt,
    updatedAt: node.updatedAt,
    year: node.year,
    x: node.layout.x ?? 0,
    y: node.layout.y ?? 0,
  };
}

export function graphToUniverse(graph: ApiGraph, expandedUniverse = process.env.NEXT_PUBLIC_EXPANDED_UNIVERSE === "true"): UniverseData {
  const nodesById = new Map(graph.nodes.map((node) => [node.id, node]));
  const visibleNodes = expandedUniverse ? graph.nodes : graph.nodes.filter((node) => ["SKILL", "INTEREST", "PROJECT"].includes(node.type));
  const visibleIds = new Set(visibleNodes.map((node) => node.id));
  return {
    nodes: visibleNodes.map(apiNodeToUniverse),
    edges: graph.edges.filter((edge) => visibleIds.has(edge.source) && visibleIds.has(edge.target)).map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      type: edge.type.toLowerCase() as "belongs_to" | "relates_to" | "enabled" | "evidence_for",
      strength: Number(edge.strength),
      provenance: edge.provenance === "SYSTEM_CREATED" ? "suggested" : edge.provenance === "DEMO" ? "curated" : "observed",
      version: edge.version,
    })),
    projects: graph.projects.map((project) => {
      const projectNode = nodesById.get(project.graphNodeId);
      return {
        id: project.graphNodeId,
        projectId: project.id,
        name: project.title,
        description: project.description ?? "",
        domains: [domains.has(projectNode?.domain as DomainId) ? projectNode?.domain as DomainId : "ideas"],
        concepts: project.conceptNodeIds.map((id) => nodesById.get(id)?.title).filter(Boolean) as string[],
        year: projectNode?.year ?? new Date().getFullYear(),
        status: project.status as "IDEA" | "PLANNED" | "BUILDING" | "COMPLETED" | "ARCHIVED",
        startedAt: project.startedAt ?? undefined,
        completedAt: project.completedAt ?? undefined,
        repositoryUrl: project.repositoryUrl ?? undefined,
        liveUrl: project.liveUrl ?? undefined,
        createdAt: project.createdAt,
        updatedAt: project.updatedAt,
        version: project.version,
      };
    }),
    proofs: graph.proofs.map((proof) => ({
      id: proof.id,
      projectId: proof.projectId,
      title: proof.title,
      type: proof.type,
      description: proof.description ?? undefined,
      url: proof.url ?? undefined,
    })),
    intersections: discoveryDefinitions,
    intersectionDecisions: graph.intersections.map((decision) => ({
      definitionKey: decision.definitionKey,
      decision: decision.decision,
      contributingNodeIds: decision.contributingNodeIds,
      resultingProjectId: decision.resultingProjectId ?? undefined,
    })),
  };
}
