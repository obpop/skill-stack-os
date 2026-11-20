import type { Metadata } from "next";
import { Bricolage_Grotesque, IBM_Plex_Mono, IBM_Plex_Sans } from "next/font/google";
import "./globals.css";
import "./skill-stack.css";

const display = Bricolage_Grotesque({ subsets: ["latin"], weight: ["500", "700", "800"], variable: "--font-ss-display", display: "swap" });
const body = IBM_Plex_Sans({ subsets: ["latin"], weight: ["400", "500", "600"], variable: "--font-ss-body", display: "swap" });
const mono = IBM_Plex_Mono({ subsets: ["latin"], weight: ["500"], variable: "--font-ss-mono", display: "swap" });

const siteUrl = process.env.NEXT_PUBLIC_SITE_URL?.trim() || "http://localhost:3000";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: "Skill Stack",
  description: "Know the next skill worth building. Skill Stack reads the skills you have, finds the one your stack is missing, and explains why.",
  applicationName: "Skill Stack",
  openGraph: {
    title: "Skill Stack",
    description: "What skill should I build next, and why? Open source, runs on your machine.",
    siteName: "Skill Stack",
    type: "website",
  },
  twitter: { card: "summary_large_image", title: "Skill Stack", description: "What skill should I build next, and why?" },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={`${display.variable} ${body.variable} ${mono.variable}`}>
      <body>{children}</body>
    </html>
  );
}
