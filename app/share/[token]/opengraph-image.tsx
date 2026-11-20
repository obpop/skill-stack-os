import { notFound } from "next/navigation";
import { getPublicProfile } from "@/lib/public-api";
import { publicOgSize, publicProfileImage } from "@/lib/public-og";

export const alt = "A selectively shared Skill Stack summary";
export const size = publicOgSize;
export const contentType = "image/png";

export default async function Image({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  const profile = await getPublicProfile("shares", token);
  if (!profile) notFound();
  return publicProfileImage(profile);
}
