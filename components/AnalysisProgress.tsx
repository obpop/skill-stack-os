"use client";

import type { BackgroundJob } from "@/lib/api/types";

const stageLabel: Record<BackgroundJob["stage"], string> = {
  READING_STACK: "Reading your stack",
  COMPARING_COMBINATIONS: "Comparing combinations",
  CHECKING_CATALOG: "Checking catalog matches",
  PREPARING_RECOMMENDATIONS: "Preparing recommendations",
  COMPLETED: "Analysis complete",
};

export function AnalysisProgress({ job, error, onCancel, onRetry }: {
  job: BackgroundJob | null;
  error: string | null;
  onCancel?: () => void;
  onRetry?: () => void;
}) {
  if (!job && !error) return null;
  if (job?.status === "SUCCEEDED" && !error) return null;
  const active = job && ["QUEUED", "RUNNING", "RETRY_SCHEDULED"].includes(job.status);
  const cancelled = job?.status === "CANCELLED";
  return (
    <aside className={`analysis-progress ${active ? "active" : "terminal"}`} aria-live="polite">
      <span className="analysis-progress-mark" aria-hidden="true" />
      <div>
        <strong>{active ? stageLabel[job.stage] : cancelled ? "Analysis cancelled" : "Analysis paused"}</strong>
        <small>{active
          ? "You can leave this page. Your last saved recommendation stays visible."
          : error ?? "Your last saved recommendation is still available."}</small>
      </div>
      {active && onCancel && <button type="button" onClick={onCancel}>Cancel</button>}
      {!active && onRetry && <button type="button" onClick={onRetry}>Try again</button>}
    </aside>
  );
}
