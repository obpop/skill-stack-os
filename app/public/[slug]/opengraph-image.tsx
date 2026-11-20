import { notFound } from "next/navigation";
import { getPublicProfile } from "@/lib/public-api";
import { publicOgSize, publicProfileImage } from "@/lib/public-og";

export const alt = "A selectively published Skill Stack summary";
export const size = publicOgSize;
export const contentType = "image/png";

export default async function Image({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const profile = await getPublicProfile("profiles", slug);
  if (!profile) notFound();
  return publicProfileImage(profile);
}
