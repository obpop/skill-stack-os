import "server-only";

import { cache } from "react";
import type { PublicProfileView } from "@/lib/api/types";

function apiBase() {
  const configured = process.env.INTERNAL_API_URL?.trim();
  if (configured) return configured.replace(/\/$/, "");
  const browserBase = process.env.NEXT_PUBLIC_API_URL?.trim();
  if (browserBase?.startsWith("http://") || browserBase?.startsWith("https://")) return browserBase.replace(/\/$/, "");
  return "http://localhost:8080/api/v1";
}

export const getPublicProfile = cache(async (kind: "profiles" | "shares", value: string): Promise<PublicProfileView | null> => {
  if (!value || value.length > 100) return null;
  const response = await fetch(`${apiBase()}/public/${kind}/${encodeURIComponent(value)}`, {
    cache: "no-store",
    headers: { Accept: "application/json" },
  }).catch(() => null);
  if (!response || response.status === 404) return null;
  if (!response.ok) throw new Error("The public Skill Stack is temporarily unavailable.");
  return response.json() as Promise<PublicProfileView>;
});
