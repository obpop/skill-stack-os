import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PublicProfileView } from "@/components/PublicProfileView";
import { getPublicProfile } from "@/lib/public-api";

type Props = { params: Promise<{ token: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { token } = await params;
  const profile = await getPublicProfile("shares", token);
  if (!profile) return { title: "Skill Stack share revoked", robots: { index: false, follow: false } };
  const title = profile.displayName ? `${profile.displayName}'s shared Skill Stack` : "A shared Skill Stack";
  const description = profile.sentence ?? "A selective Skill Stack shared by its owner.";
  return { title, description, openGraph: { title, description, type: "profile" }, twitter: { card: "summary_large_image", title, description }, robots: { index: false, follow: false, noarchive: true } };
}

export default async function SharedProfilePage({ params }: Props) {
  const { token } = await params;
  const profile = await getPublicProfile("shares", token);
  if (!profile) notFound();
  return <PublicProfileView profile={profile} />;
}
