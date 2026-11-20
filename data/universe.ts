import type {
  DomainId,
  IntersectionDefinition,
  NodeType,
  ProjectDefinition,
  UniverseData,
  UniverseEdge,
  UniverseNode,
} from "@/lib/types";

export const domainMeta: Record<
  DomainId,
  { label: string; color: string; x: number; y: number; thesis: string }
> = {
  engineering: {
    label: "Engineering",
    color: "#64A8C4",
    x: -27,
    y: 12,
    thesis: "Systems that remain intelligible under pressure.",
  },
  ai: {
    label: "Artificial Intelligence",
    color: "#8AA7D8",
    x: -7,
    y: 22,
    thesis: "Intelligence as a medium, tool and design problem.",
  },
  business: {
    label: "Business",
    color: "#B89A68",
    x: 19,
    y: 19,
    thesis: "Turning useful ideas into durable systems.",
  },
  economics: {
    label: "Economics & Investing",
    color: "#9BB97A",
    x: 28,
    y: -1,
    thesis: "How incentives, capital and time shape outcomes.",
  },
  ideas: {
    label: "Ideas",
    color: "#D0BE8B",
    x: 5,
    y: 2,
    thesis: "Questions that connect otherwise separate worlds.",
  },
  culture: {
    label: "Culture & Design",
    color: "#B889A3",
    x: 18,
    y: -22,
    thesis: "Taste, meaning and designed experience.",
  },
  world: {
    label: "World",
    color: "#799C95",
    x: -9,
    y: -24,
    thesis: "The systems beneath events and societies.",
  },
  health: {
    label: "Health",
    color: "#B47D6B",
    x: -29,
    y: -15,
    thesis: "Energy and resilience as compounding assets.",
  },
  communication: {
    label: "Communication",
    color: "#A994C2",
    x: -4,
    y: -8,
    thesis: "Making complex things clear enough to travel.",
  },
};

type ConceptSeed = [name: string, type: NodeType, importance?: number, description?: string];

const concepts: Record<DomainId, ConceptSeed[]> = {
  engineering: [
    ["Backend development with Spring Boot", "SKILL", 9, "Building reliable backend services and APIs with Spring Boot."],
    ["Spring Boot", "SKILL", 8],
    ["Distributed Systems", "KNOWLEDGE", 10, "Coordination, failure and consistency across machines."],
    ["RabbitMQ", "SKILL", 7],
    ["PostgreSQL", "SKILL", 8],
    ["Docker", "SKILL", 7],
    ["Kubernetes", "KNOWLEDGE", 7],
    ["Observability", "KNOWLEDGE", 8],
    ["Security", "KNOWLEDGE", 7],
    ["System Design", "SKILL", 9],
    ["APIs", "SKILL", 8],
    ["Cloud", "KNOWLEDGE", 6],
    ["Event-driven Systems", "KNOWLEDGE", 9],
    ["Messaging", "KNOWLEDGE", 7],
    ["Queues", "KNOWLEDGE", 6],
    ["Retries", "KNOWLEDGE", 5],
    ["Idempotency", "KNOWLEDGE", 7],
    ["Consistency", "KNOWLEDGE", 7],
    ["Resilience", "IDEA", 7],
    ["Architecture", "SKILL", 8],
  ],
  ai: [
    ["AI", "INTEREST", 10, "A general-purpose technology and a new design material."],
    ["LLMs", "KNOWLEDGE", 9],
    ["AI Infrastructure", "KNOWLEDGE", 8],
    ["Embeddings", "KNOWLEDGE", 7],
    ["Agents", "INTEREST", 8],
    ["RAG", "KNOWLEDGE", 7],
    ["AI Coding", "INTEREST", 8],
    ["Machine Learning", "KNOWLEDGE", 7],
    ["Human-AI Interaction", "INTEREST", 9],
    ["Evaluation", "KNOWLEDGE", 6],
    ["Tool Use", "KNOWLEDGE", 7],
    ["Context Engineering", "SKILL", 7],
    ["Model Behavior", "KNOWLEDGE", 6],
    ["Retrieval", "KNOWLEDGE", 7],
    ["AI Safety", "INTEREST", 6],
    ["Reasoning Systems", "INTEREST", 8],
    ["Intelligence Interfaces", "IDEA", 9],
  ],
  business: [
    ["Startups", "INTEREST", 9],
    ["SaaS", "KNOWLEDGE", 7],
    ["Sales", "SKILL", 6],
    ["Marketing", "KNOWLEDGE", 6],
    ["Product", "SKILL", 9],
    ["Entrepreneurship", "GOAL", 9],
    ["Leverage", "IDEA", 8],
    ["Strategy", "SKILL", 8],
    ["Distribution", "KNOWLEDGE", 7],
    ["Customer Research", "SKILL", 7],
    ["Pricing", "KNOWLEDGE", 6],
    ["Positioning", "KNOWLEDGE", 7],
    ["Network Effects", "IDEA", 7],
    ["Founder Craft", "INTEREST", 7],
    ["Operations", "KNOWLEDGE", 6],
    ["Independent Business", "GOAL", 8],
  ],
  economics: [
    ["Investing", "INTEREST", 10, "The allocation of capital under uncertainty."],
    ["Economics", "KNOWLEDGE", 10, "Incentives, institutions and trade-offs."],
    ["Markets", "KNOWLEDGE", 8],
    ["Capital Allocation", "SKILL", 8],
    ["Fintech", "INDUSTRY", 8],
    ["Compounding", "IDEA", 9],
    ["Business Models", "KNOWLEDGE", 8],
    ["Market Structure", "KNOWLEDGE", 7],
    ["Payments", "INDUSTRY", 7],
    ["Banking", "INDUSTRY", 6],
    ["Financial Ledgers", "KNOWLEDGE", 7],
    ["Settlement", "KNOWLEDGE", 6],
    ["Portfolio Systems", "KNOWLEDGE", 7],
    ["Risk", "KNOWLEDGE", 8],
    ["Behavioral Finance", "INTEREST", 7],
    ["Incentives", "IDEA", 8],
    ["Game Theory", "KNOWLEDGE", 6],
    ["Market Data", "KNOWLEDGE", 7],
  ],
  ideas: [
    ["Systems Thinking", "IDEA", 10],
    ["Human Potential", "INTEREST", 10],
    ["Learning", "INTEREST", 9],
    ["Creativity", "INTEREST", 8],
    ["Technology", "INTEREST", 9],
    ["Innovation", "IDEA", 7],
    ["Decision Making", "SKILL", 8],
    ["Identity", "IDEA", 9],
    ["Curiosity", "IDENTITY", 10],
    ["Agency", "IDEA", 8],
    ["Mental Models", "KNOWLEDGE", 8],
    ["Second-order Effects", "IDEA", 7],
    ["Complexity", "INTEREST", 7],
    ["Emergence", "IDEA", 8],
    ["Direction", "GOAL", 8],
    ["Personal Knowledge", "KNOWLEDGE", 8],
    ["Taste", "IDENTITY", 9],
  ],
  culture: [
    ["Music", "INTEREST", 9],
    ["Design", "SKILL", 9],
    ["Photography", "INTEREST", 7],
    ["Art", "INTEREST", 8],
    ["Digital Experiences", "INTEREST", 8],
    ["Storytelling", "SKILL", 8],
    ["Typography", "INTEREST", 6],
    ["Interaction Design", "SKILL", 8],
    ["Editorial Design", "INTEREST", 7],
    ["Museums", "INTEREST", 6],
    ["Sound", "INTEREST", 6],
    ["Albums", "INTEREST", 5],
    ["Visual Culture", "KNOWLEDGE", 7],
    ["Aesthetics", "IDEA", 8],
    ["Creative Direction", "SKILL", 8],
    ["Information Design", "SKILL", 8],
  ],
  world: [
    ["Geopolitics", "INTEREST", 9],
    ["History", "KNOWLEDGE", 8],
    ["Society", "INTEREST", 7],
    ["Infrastructure", "INTEREST", 8],
    ["Global Trade", "KNOWLEDGE", 7],
    ["Institutions", "KNOWLEDGE", 8],
    ["Energy", "INDUSTRY", 7],
    ["Supply Chains", "KNOWLEDGE", 7],
    ["Demographics", "KNOWLEDGE", 6],
    ["Cities", "INTEREST", 6],
    ["Power", "IDEA", 7],
    ["Technology Policy", "INTEREST", 7],
    ["Industrial Capacity", "KNOWLEDGE", 6],
    ["Climate", "INTEREST", 6],
    ["International Relations", "KNOWLEDGE", 7],
    ["Political Economy", "KNOWLEDGE", 8],
  ],
  health: [
    ["Strength", "INTEREST", 8],
    ["Running", "INTEREST", 7],
    ["Recovery", "KNOWLEDGE", 7],
    ["Sleep", "KNOWLEDGE", 8],
    ["Longevity", "INTEREST", 8],
    ["Nutrition", "KNOWLEDGE", 6],
    ["Mobility", "SKILL", 5],
    ["Stress", "KNOWLEDGE", 6],
    ["Energy Management", "SKILL", 7],
    ["Habits", "KNOWLEDGE", 7],
    ["Adaptation", "IDEA", 6],
    ["Endurance", "SKILL", 6],
    ["Focus", "SKILL", 8],
    ["Wellbeing", "GOAL", 7],
  ],
  communication: [
    ["Writing", "SKILL", 9],
    ["Teaching", "SKILL", 8],
    ["Content", "SKILL", 7],
    ["Research", "SKILL", 9],
    ["Explanation", "SKILL", 8],
    ["Essays", "PROOF_OF_WORK", 7],
    ["Public Thinking", "GOAL", 7],
    ["Synthesis", "SKILL", 9],
    ["Argument", "SKILL", 6],
    ["Knowledge Sharing", "GOAL", 7],
    ["Visual Explanation", "SKILL", 7],
    ["Audience", "KNOWLEDGE", 6],
    ["Editing", "SKILL", 7],
    ["Interviewing", "SKILL", 5],
    ["Documentation", "SKILL", 7],
  ],
};

export const projects: ProjectDefinition[] = [
  {
    id: "project-skill-stack",
    name: "Skill Stack",
    description: "A personal system for choosing the next skill worth building.",
    domains: ["ai", "engineering", "ideas", "culture"],
    concepts: ["AI", "System Design", "Human Potential", "Identity", "Information Design"],
    year: 2026,
  },
  {
    id: "project-music-room",
    name: "Music Room",
    description: "An immersive environment for exploring an artist, album and influence graph.",
    domains: ["culture", "engineering"],
    concepts: ["Music", "Digital Experiences", "Design", "Technology"],
    year: 2025,
  },
  {
    id: "project-world",
    name: "World",
    description: "An intelligence interface for events and their second-order effects.",
    domains: ["world", "economics", "ai"],
    concepts: ["Geopolitics", "Economics", "AI", "Investing", "Second-order Effects"],
    year: 2026,
  },
  {
    id: "project-compound",
    name: "Compound",
    description: "A map of how capital, knowledge, health and reputation compound.",
    domains: ["ideas", "economics", "health"],
    concepts: ["Human Potential", "Compounding", "Systems Thinking", "Health"],
    year: 2025,
  },
  {
    id: "project-distributed-lab",
    name: "Distributed Systems Lab",
    description: "An educational environment for observing systems under failure.",
    domains: ["engineering", "communication", "culture"],
    concepts: ["Distributed Systems", "Teaching", "Visual Explanation", "Observability"],
    year: 2024,
  },
  {
    id: "project-incident-investigator",
    name: "AI Incident Investigator",
    description: "A concept for investigating incidents across logs, metrics and deployments.",
    domains: ["ai", "engineering"],
    concepts: ["AI", "Observability", "AI Infrastructure", "Distributed Systems"],
    year: 2026,
  },
];

export const intersections: IntersectionDefinition[] = [
  {
    id: "intersection-financial-intelligence",
    name: "Financial Intelligence Systems",
    required: ["Backend development with Spring Boot", "AI", "Investing", "Economics"],
    supporting: [
      "Distributed Systems",
      "Event-driven Systems",
      "Markets",
      "APIs",
      "Market Data",
      "Fintech",
      "Financial Ledgers",
    ],
    why: "Backend engineering and event-driven architecture intersect with AI, economics and investing.",
    project: "Build an event-driven investment intelligence engine.",
    explore: ["Financial infrastructure", "Market data", "Portfolio systems", "Payment architecture"],
    learn: ["Double-entry ledgers", "Market structure", "Settlement systems"],
    direction: "Financial infrastructure engineering",
  },
  {
    id: "intersection-learning-studio",
    name: "Adaptive Learning Studio",
    required: ["AI", "Teaching", "Learning", "Design"],
    supporting: ["Human-AI Interaction", "Synthesis", "Interaction Design", "Visual Explanation"],
    why: "Teaching and learning become a designed, adaptive experience when paired with AI and interaction design.",
    project: "Prototype a visual tutor that adapts explanations to a learner's knowledge graph.",
    explore: ["Knowledge tracing", "Interactive explanation", "Learning science"],
    learn: ["Pedagogy", "Model evaluation", "Accessibility patterns"],
    direction: "AI learning experience design",
  },
  {
    id: "intersection-resilient-agency",
    name: "Resilient Personal Systems",
    required: ["Systems Thinking", "Health", "Compounding"],
    supporting: ["Habits", "Sleep", "Energy Management", "Decision Making"],
    why: "Systems thinking connects daily energy, repeated behavior and long-term compounding.",
    project: "Design a quiet personal operating system centered on energy and agency.",
    explore: ["Behavior design", "Recovery systems", "Personal analytics"],
    learn: ["Circadian rhythm", "Habit formation", "Measurement design"],
    direction: "Human performance systems",
  },
];

const slug = (value: string) =>
  value
    .toLowerCase()
    .replace(/&/g, "and")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");

const yearFor = (domainIndex: number, conceptIndex: number) =>
  2024 + ((domainIndex * 2 + conceptIndex) % 3);

export function buildUniverse(limit = Number.POSITIVE_INFINITY): UniverseData {
  const nodes: UniverseNode[] = [];
  const edges: UniverseEdge[] = [];
  const nameToId = new Map<string, string>();
  const domains = Object.entries(domainMeta) as [DomainId, (typeof domainMeta)[DomainId]][];

  for (const [domainIndex, [domain, meta]] of domains.entries()) {
    const domainId = `domain-${domain}`;
    nodes.push({
      id: domainId,
      type: "DOMAIN",
      name: meta.label,
      domain,
      importance: 10,
      depth: 10,
      activity: 8,
      description: meta.thesis,
      year: 2024,
      x: meta.x,
      y: meta.y,
    });
    nameToId.set(meta.label, domainId);

    concepts[domain].forEach(([name, type, importance = 6, description], index) => {
      if (nodes.length >= limit) return;
      const id = `${domain}-${slug(name)}`;
      const angle = index * 2.399963 + domainIndex * 0.41;
      const ring = 4.6 + (index % 5) * 1.55;
      nodes.push({
        id,
        type,
        name,
        domain,
        importance,
        depth: Math.max(3, importance - 1),
        activity: 3 + ((index * 7 + domainIndex) % 7),
        proofOfWork: type === "PROOF_OF_WORK",
        description,
        year: yearFor(domainIndex, index),
        x: meta.x + Math.cos(angle) * ring,
        y: meta.y + Math.sin(angle) * ring * 0.76,
      });
      nameToId.set(name, id);
      edges.push({
        id: `edge-${domainId}-${id}`,
        source: domainId,
        target: id,
        type: "belongs_to",
        strength: 0.34 + importance * 0.045,
        provenance: "curated",
      });
      if (index > 0 && index % 3 !== 0) {
        const previous = concepts[domain][index - 1][0];
        const previousId = nameToId.get(previous);
        if (previousId) {
          edges.push({
            id: `edge-${previousId}-${id}`,
            source: previousId,
            target: id,
            type: "relates_to",
            strength: 0.28 + (index % 4) * 0.09,
            provenance: "observed",
          });
        }
      }
    });
  }

  const crossLinks: [string, string, number][] = [
    ["Backend development with Spring Boot", "Distributed Systems", 0.9],
    ["RabbitMQ", "Event-driven Systems", 0.95],
    ["Event-driven Systems", "Financial Ledgers", 0.77],
    ["APIs", "Fintech", 0.68],
    ["AI", "Human-AI Interaction", 0.88],
    ["AI", "Investing", 0.56],
    ["AI Infrastructure", "Cloud", 0.81],
    ["Embeddings", "Personal Knowledge", 0.74],
    ["Intelligence Interfaces", "Information Design", 0.91],
    ["Product", "Interaction Design", 0.72],
    ["Startups", "Technology", 0.7],
    ["Strategy", "Decision Making", 0.79],
    ["Economics", "Geopolitics", 0.84],
    ["Markets", "Global Trade", 0.66],
    ["Compounding", "Human Potential", 0.89],
    ["Incentives", "Institutions", 0.83],
    ["Systems Thinking", "Distributed Systems", 0.84],
    ["Systems Thinking", "Economics", 0.78],
    ["Learning", "Teaching", 0.9],
    ["Creativity", "Music", 0.69],
    ["Identity", "Storytelling", 0.72],
    ["Taste", "Design", 0.91],
    ["Research", "Geopolitics", 0.65],
    ["Synthesis", "Systems Thinking", 0.88],
    ["Writing", "Public Thinking", 0.87],
    ["Focus", "Deep Work", 0.5],
    ["Sleep", "Energy Management", 0.87],
    ["Longevity", "Compounding", 0.74],
    ["Infrastructure", "Distributed Systems", 0.63],
    ["Political Economy", "Economics", 0.92],
  ];

  crossLinks.forEach(([sourceName, targetName, strength], index) => {
    const source = nameToId.get(sourceName);
    const target = nameToId.get(targetName);
    if (source && target) {
      edges.push({
        id: `cross-${index}-${source}-${target}`,
        source,
        target,
        type: "relates_to",
        strength,
        provenance: "observed",
      });
    }
  });

  for (const [index, project] of projects.entries()) {
    if (nodes.length >= limit) break;
    const anchors = project.concepts.map((name) => nameToId.get(name)).filter(Boolean) as string[];
    const anchorNodes = anchors
      .map((id) => nodes.find((node) => node.id === id))
      .filter(Boolean) as UniverseNode[];
    const x = anchorNodes.reduce((sum, node) => sum + node.x, 0) / Math.max(1, anchorNodes.length);
    const y = anchorNodes.reduce((sum, node) => sum + node.y, 0) / Math.max(1, anchorNodes.length);
    nodes.push({
      id: project.id,
      type: "PROJECT",
      name: project.name,
      domain: project.domains[0],
      importance: 9,
      depth: 8,
      activity: 9,
      proofOfWork: true,
      description: project.description,
      year: project.year,
      x: x + Math.cos(index) * 1.8,
      y: y + Math.sin(index) * 1.8,
    });
    nameToId.set(project.name, project.id);
    anchors.forEach((target, edgeIndex) =>
      edges.push({
        id: `project-edge-${index}-${edgeIndex}`,
        source: project.id,
        target,
        type: "evidence_for",
        strength: 0.86,
        provenance: "curated",
      }),
    );
  }

  return { nodes: nodes.slice(0, limit), edges: edges.filter((edge) => nodes.some((n) => n.id === edge.source) && nodes.some((n) => n.id === edge.target)), projects, intersections };
}

export const universe = buildUniverse();

export function findNodeByName(name: string, data: UniverseData = universe) {
  return data.nodes.find((node) => node.name.toLowerCase() === name.toLowerCase());
}

export function chooseIntersection(selectedNodes: UniverseNode[], definitions: IntersectionDefinition[] = intersections, excluded = new Set<string>()): IntersectionDefinition {
  const selectedNames = new Set(selectedNodes.map((node) => node.name));
  const available = definitions.filter((intersection) => !excluded.has(intersection.id));
  const scored = available
    .map((intersection) => ({
      intersection,
      score: intersection.required.filter((name) => selectedNames.has(name)).length,
    }))
    .sort((a, b) => b.score - a.score);
  return scored[0]?.score > 0 ? scored[0].intersection : available[0] ?? definitions[0];
}
