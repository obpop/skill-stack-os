export type ApiUser = {
  id: string;
  subject: string;
  displayName: string;
  onboardingComplete: boolean;
  defaultDomain: string;
};

export type ApiNode = {
  id: string;
  externalKey: string | null;
  type: string;
  title: string;
  description: string | null;
  domain: string;
  importance: number;
  depth: number;
  status: string;
  provenance: string;
  proofOfWork: boolean;
  level: "LEARNING" | "GOOD" | "TOP_25" | null;
  monetizable: boolean;
  learnOrOutsource: "LEARN" | "OUTSOURCE" | null;
  wouldDoForFree: boolean;
  effectiveLevel: "LEARNING" | "GOOD" | "TOP_25" | "PROVEN" | null;
  provenByProjectIds: string[];
  year: number;
  layout: { x: number | null; y: number | null };
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ApiEdge = {
  id: string;
  source: string;
  target: string;
  type: string;
  strength: number;
  provenance: string;
  version: number;
};

export type ApiProject = {
  id: string;
  graphNodeId: string;
  title: string;
  description: string | null;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
  repositoryUrl: string | null;
  liveUrl: string | null;
  conceptNodeIds: string[];
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ApiProof = {
  id: string;
  projectId: string;
  title: string;
  type: string;
  description: string | null;
  url: string | null;
};

export type ApiIntersection = {
  id: string;
  definitionKey: string;
  title: string;
  decision: "SAVED" | "REJECTED" | "CONVERTED";
  contributingNodeIds: string[];
  resultingProjectId: string | null;
};

export type ApiGraph = {
  user: ApiUser;
  nodes: ApiNode[];
  edges: ApiEdge[];
  projects: ApiProject[];
  proofs: ApiProof[];
  intersections: ApiIntersection[];
  metadata: { nodeCount: number; edgeCount: number; generatedAt: string; source: string };
};

export type ApiSearch = {
  nodes: ApiNode[];
  metadata: { query: string; count: number; strategy: string };
};

export type DevSession = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  subject: string;
  displayName: string;
};

export type Auth0Session = {
  accessToken: string;
  expiresAt: string;
  csrfHeaderName: string;
  csrfToken: string;
};

export type Auth0Logout = {
  logoutUrl: string;
};

export type ApiProblem = {
  timestamp?: string;
  status?: number;
  code?: string;
  message?: string;
  requestId?: string;
  fieldErrors?: Record<string, string>;
};

export type CareerRole = "STUDENT" | "EMPLOYEE" | "ENTREPRENEUR" | "FREELANCER";
export type SkillDirection = "DEEPEN_FIELD" | "SPECIALIZE" | "COMBINE" | "SWITCH_FIELD" | "NOT_SURE";
export type WorkPreference = "STAY_IN_CITY" | "OPEN_TO_MOVE" | "REMOTE";
export type HelpTarget = "BUSINESSES" | "CREATORS" | "STUDENTS" | "A_CAUSE";
export type ApiProfile = { displayName: string; city: string | null; country: string | null; currentRole: CareerRole | null; targetRole: CareerRole | null; buildOnTheSide: boolean; timelineYears: number | null; skillDirection: SkillDirection | null; focusSkillIds: string[]; workPreference: WorkPreference | null; helpTarget: HelpTarget | null; onboardingCompletedAt: string | null; complete: boolean; version: number };
export type StackPattern = { label: string; evidence: string };
export type StackQuadrant = { id: string; title: string; label: string; energyHigh: boolean; demandHigh: boolean; signals: string[]; skillIds: string[] };
export type StackSummary = { strongSkillCount: number; illustrativeRarity: string; rarityExplanation: string; sentence: string; patterns: StackPattern[]; recommendedStage: "ACQUIRE" | "GET_PAID" | "BUILD_LEVERAGE"; stageReason: string; quadrants: StackQuadrant[] };
export type StackDiscovery = { id: string; title: string; why: string; build: string; learn: string[]; career: string; quadrant: StackQuadrant; sourceSkillIds: string[] };
export type DevelopmentRecommendation = { id: string; section: string; subsection: string | null; title: string; reason: string; costLabel: string; formatLabel: string; lever: string; url: string | null };
export type DevelopResult = { nextSkill: { title: string; alreadyOwned: boolean; reasons: string[]; levers: string[]; sourceSkillIds: string[] }; education: DevelopmentRecommendation[]; networks: DevelopmentRecommendation[]; moves: DevelopmentRecommendation[]; build: DevelopmentRecommendation[]; caveats: string[] };
export type ImportProposal = { id: string; type: "SKILL" | "INTEREST" | "PROJECT"; title: string; suggestedLevel: "LEARNING" | "GOOD" | "TOP_25" | null; evidence: string; provenance: "DETERMINISTIC_MATCH" | "AI_SUGGESTION_UNVERIFIED"; uncertainty: string; duplicate: boolean };
export type ImportJob = { id: string; sourceType: "TEXT" | "MARKDOWN"; status: "PENDING" | "EXTRACTED" | "REVIEWED" | "FAILED"; proposals: ImportProposal[]; errorCode: string | null; createdAt: string; updatedAt: string; acceptedAt: string | null };

export type IntelligenceDecision = "UNREVIEWED" | "SAVED" | "REJECTED" | "MODIFIED" | "HIDDEN" | "EXPLORED";
export type IntelligenceStrategy = "COMPLEMENTARY" | "SPECIALIST_AMPLIFIER" | "BRIDGE_DOMAINS" | "TECHNICAL_COMMERCIAL" | "CREATOR_DISTRIBUTION" | "OPERATOR_AUTOMATION" | "CAREER_PIVOT" | "DEPTH_BEFORE_BREADTH" | "INCOME_STACK" | "LEVERAGE_STACK" | "LEARN_PARTNER_OUTSOURCE" | "PASSION_TO_PROOF";
export type IntelligenceEvidence = { provenance: "USER_FACT" | "CURATED_VERIFIED_RESOURCE" | "RULE_DERIVED" | "AI_SUGGESTION_UNVERIFIED" | "USER_ACCEPTED_AI_DERIVATION"; sourceType: string; sourceId: string; label: string; reason: string };
export type IntelligenceStrategyOption = {
  id: string; strategy: IntelligenceStrategy; title: string; recommended: boolean; summary: string;
  contributions: { skillId: string; title: string; contribution: string; evidence: string }[];
  synergy: string; plausibleApplications: string[]; limitations: string[]; missingCapability: string;
  tradeoff: string; firstAction: string; evidence: IntelligenceEvidence[];
};
export type IntelligenceRecommendation = {
  recommendationId: string; generatedAt: string; inputVersion: string;
  engine: "DETERMINISTIC" | "AI_ASSISTED" | "AI_FALLBACK"; recommendedStrategyId: string;
  headline: string; summary: string; contributingSkillIds: string[]; strategies: IntelligenceStrategyOption[];
  nextSkill: { title: string; ownedSkillId: string | null; whyNow: string; alternatives: { title: string; tradeoff: string; uncertainty: string }[]; levers: string[]; firstAction: string; evidenceOutcome: string };
  development: { educationItemIds: string[]; networkItemIds: string[]; moves: string[]; buildProjectSuggestion: string; firstAction: string };
  why: { evidence: IntelligenceEvidence[]; profileFieldsUsed: string[]; assumptions: string[]; tradeoffs: string[]; resourceVerification: string; retrievalReasons: string[] };
  userDecision: IntelligenceDecision; selectedStrategyId: string | null;
  provenance: { schemaVersion: string; provider: string; model: string; operationId: string | null; cacheHit: boolean; boundedContextCharacters: number; sourceCount: number; costState: string };
};

export type AiStatus = {
  enabled: boolean; provider: string; model: string; monthlyBudgetUsd: number; totalBudgetUsd: number;
  maxCallsPerRequest: number; maxInputCharacters: number; maxCallsPerUserMonth: number;
  maxEstimatedCostPerCallUsd: number; state: string;
  monthlyReservedUsd: number; totalReservedUsd: number; totalEstimatedUsd: number;
};
export type BackgroundJobStatus = "QUEUED" | "RUNNING" | "RETRY_SCHEDULED" | "SUCCEEDED" | "FAILED" | "CANCELLED";
export type BackgroundJobStage = "READING_STACK" | "COMPARING_COMBINATIONS" | "CHECKING_CATALOG" | "PREPARING_RECOMMENDATIONS" | "COMPLETED";
export type BackgroundJob = {
  id: string; operationType: "RECOMMENDATION"; status: BackgroundJobStatus; stage: BackgroundJobStage;
  attemptCount: number; maxAttempts: number; resultId: string | null; safeErrorCode: string | null;
  cancellationRequested: boolean; availableAt: string; createdAt: string; updatedAt: string; completedAt: string | null;
};

export type PublicTheme = "OBSIDIAN" | "PAPER" | "SUNRISE";
export type PublicationStatus = "DRAFT" | "PUBLISHED";
export type TimelineEventType = "SKILL_ADDED" | "SKILL_LEVEL_CHANGED" | "INTEREST_ADDED" | "NEXT_SKILL_SELECTED" | "ACTION_COMPLETED" | "PROJECT_LINKED" | "PROJECT_COMPLETED" | "PROOF_ADDED" | "PERSONAL_MILESTONE";
export type TimelineDateSource = "SERVER_RECORDED" | "USER_ENTERED";

export type PublicProfileView = {
  slug: string;
  displayName: string | null;
  theme: PublicTheme;
  publishedAt: string | null;
  sentence: string | null;
  illustrativeRarity: string | null;
  rarityExplanation: string | null;
  skills: { name: string; level: string | null; projectBacked: boolean }[];
  intersections: { name: string; explanation: string; tradeoff: string; firstAction: string }[];
  developmentFocus: { name: string; whyNow: string; firstAction: string; evidenceOutcome: string } | null;
  projects: {
    title: string; description: string | null; stage: string; startedAt: string | null; completedAt: string | null;
    repositoryUrl: string | null; liveUrl: string | null; skillsApplied: string[];
    proof: { title: string; type: string; description: string | null; url: string | null }[];
  }[];
  timeline: { type: string; title: string; description: string | null; occurredOn: string | null; recordedAt: string; dateSource: TimelineDateSource }[];
  limitations: string[];
  attribution: string;
};

export type PublicationConfig = {
  slug: string; theme: PublicTheme; status: PublicationStatus;
  showDisplayName: boolean; showSentence: boolean; showSkillLevels: boolean;
  showIntersections: boolean; showNextSkill: boolean; showProjects: boolean;
  showProof: boolean; showMilestones: boolean; showRarity: boolean;
  selectedSkillIds: string[]; selectedStrategyIds: string[]; selectedProjectIds: string[];
  selectedProofIds: string[]; selectedMilestoneIds: string[];
  publishedAt: string | null; unpublishedAt: string | null; version: number;
};

export type PublicationTimeline = {
  id: string; type: TimelineEventType; title: string; description: string | null;
  occurredOn: string | null; recordedAt: string; dateSource: TimelineDateSource; sourceType: string | null;
};

export type PublicationShare = {
  id: string; label: string | null; tokenHint: string; createdAt: string; revokedAt: string | null;
};

export type PublicationOwner = {
  config: PublicationConfig;
  preview: PublicProfileView;
  timeline: PublicationTimeline[];
  shares: PublicationShare[];
  publicPath: string;
};

export type PublicationShareCreated = {
  id: string; token: string; path: string; label: string | null; createdAt: string;
};
