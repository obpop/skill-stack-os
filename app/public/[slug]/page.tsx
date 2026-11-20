import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PublicProfileView } from "@/components/PublicProfileView";
import { getPublicProfile } from "@/lib/public-api";

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const profile = await getPublicProfile("profiles", slug);
  if (!profile) return { title: "Skill Stack not available", robots: { index: false, follow: false } };
  const title = profile.displayName ? `${profile.displayName}'s Skill Stack` : "A public Skill Stack";
  const description = profile.sentence ?? "A selective, evidence-aware view of skills, development focus and proof.";
  return {
    title,
    description,
    alternates: { canonical: `/public/${profile.slug}` },
    openGraph: { title, description, type: "profile" },
    twitter: { card: "summary_large_image", title, description },
    robots: { index: true, follow: true },
  };
}

export default async function PublicProfilePage({ params }: Props) {
  const { slug } = await params;
  const profile = await getPublicProfile("profiles", slug);
  if (!profile) notFound();
  return <PublicProfileView profile={profile} />;
}
