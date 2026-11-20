export type NodeType =
  | "IDENTITY"
  | "INTEREST"
  | "SKILL"
  | "KNOWLEDGE"
  | "VALUE"
  | "GOAL"
  | "IDEA"
  | "PROJECT"
  | "PERSON"
  | "BOOK"
  | "INDUSTRY"
  | "PROOF_OF_WORK"
  | "DOMAIN"
  | "INTERSECTION";

export type DomainId =
  | "engineering"
  | "ai"
  | "business"
  | "economics"
  | "ideas"
  | "culture"
  | "world"
  | "health"
  | "communication";

export type NodeStatus = "CURIOUS" | "EXPLORING" | "ACTIVE" | "DEVELOPED" | "ARCHIVED";
export type SkillLevel = "LEARNING" | "GOOD" | "TOP_25";
export type EffectiveSkillLevel = SkillLevel | "PROVEN";

export type UniverseNode = {
  id: string;
  type: NodeType;
  name: string;
  domain: DomainId;
  importance: number;
  depth: number;
  activity: number;
  proofOfWork?: boolean;
  level?: SkillLevel;
  effectiveLevel?: EffectiveSkillLevel;
  monetizable?: boolean;
  learnOrOutsource?: "LEARN" | "OUTSOURCE";
  wouldDoForFree?: boolean;
  provenByProjectIds?: string[];
  description?: string;
  status?: NodeStatus;
  createdAt?: string;
  updatedAt?: string;
  year: number;
  x: number;
  y: number;
};

export type UniverseEdge = {
  id: string;
  source: string;
  target: string;
  type: "belongs_to" | "relates_to" | "enabled" | "evidence_for";
  strength: number;
  provenance: "curated" | "observed" | "suggested";
  version?: number;
};

export type ProjectDefinition = {
  id: string;
  projectId?: string;
  name: string;
  description: string;
  domains: DomainId[];
  concepts: string[];
  year: number;
  status?: "IDEA" | "PLANNED" | "BUILDING" | "COMPLETED" | "ARCHIVED";
  startedAt?: string;
  completedAt?: string;
  repositoryUrl?: string;
  liveUrl?: string;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
};

export type ProofDefinition = {
  id: string;
  projectId: string;
  title: string;
  type: string;
  description?: string;
  url?: string;
};

export type IntersectionDefinition = {
  id: string;
  name: string;
  required: string[];
  supporting: string[];
  why: string;
  project: string;
  explore: string[];
  learn: string[];
  direction: string;
};

export type IntersectionDecisionState = {
  definitionKey: string;
  decision: "SAVED" | "REJECTED" | "CONVERTED";
  contributingNodeIds: string[];
  resultingProjectId?: string;
};

export type UniverseData = {
  nodes: UniverseNode[];
  edges: UniverseEdge[];
  projects: ProjectDefinition[];
  intersections: IntersectionDefinition[];
  intersectionDecisions?: IntersectionDecisionState[];
  proofs?: ProofDefinition[];
};

export type DiscoveryPhase =
  | "idle"
  | "reducing"
  | "retrieving"
  | "converging"
  | "revealed";
