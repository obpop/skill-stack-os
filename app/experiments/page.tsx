import { notFound } from "next/navigation";
import { EngineExperiment } from "@/components/EngineExperiment";

export default async function ExperimentsPage({ searchParams }: { searchParams: Promise<{ engine?: string; count?: string }> }) {
  const params = await searchParams;
  const engine = ["sigma", "cytoscape", "reactflow"].includes(params.engine ?? "") ? params.engine! : "sigma";
  const count = Math.min(5000, Math.max(100, Number(params.count) || 500));
  return <EngineExperiment engine={engine} count={count} />;
}
