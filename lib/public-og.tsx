import { ImageResponse } from "next/og";
import type { PublicProfileView } from "@/lib/api/types";

export const publicOgSize = { width: 1200, height: 630 };

export function publicProfileImage(profile: PublicProfileView) {
  return new ImageResponse(
    <div style={{ width: "100%", height: "100%", display: "flex", flexDirection: "column", justifyContent: "space-between", padding: "68px", background: profile.theme === "PAPER" ? "#f2eee4" : profile.theme === "SUNRISE" ? "#321d2a" : "#071312", color: profile.theme === "PAPER" ? "#172126" : "#f4f0e8", fontFamily: "Arial, sans-serif" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: 24, letterSpacing: 3, textTransform: "uppercase", color: profile.theme === "PAPER" ? "#53626a" : "#99d0ba" }}>
        <span style={{ display: "flex", alignItems: "center", gap: 16 }}><span style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 5 }}><span style={{ display: "flex", width: 26, height: 9, borderRadius: 5, background: "#587c6f" }} /><span style={{ display: "flex", width: 33, height: 9, borderRadius: 5, background: "#78a694" }} /><span style={{ display: "flex", width: 40, height: 9, borderRadius: 5, background: "#99d0ba" }} /></span>Skill Stack</span><span>Open source</span>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
        <div style={{ display: "flex", fontSize: 62, fontWeight: 700, lineHeight: 1.04 }}>{profile.displayName ? `${profile.displayName}'s Skill Stack` : "A Skill Stack in progress"}</div>
        {profile.sentence && <div style={{ display: "flex", maxWidth: 970, fontSize: 34, lineHeight: 1.3, color: profile.theme === "PAPER" ? "#36474f" : "#d2dde1" }}>{profile.sentence}</div>}
        <div style={{ display: "flex", gap: 14, flexWrap: "wrap" }}>{profile.skills.slice(0, 3).map((skill) => <span key={skill.name} style={{ display: "flex", padding: "12px 20px", border: `2px solid ${profile.theme === "PAPER" ? "#758990" : "#3d8f74"}`, borderRadius: 999, fontSize: 25 }}>{skill.name}</span>)}</div>
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
        <span style={{ display: "flex", fontSize: 25 }}>{profile.developmentFocus ? `Developing next: ${profile.developmentFocus.name}` : "Skills, choices, action, proof"}</span>
        {profile.illustrativeRarity && <span style={{ display: "flex", fontSize: 20, color: profile.theme === "PAPER" ? "#675f4a" : "#7fdde0" }}>{profile.illustrativeRarity} - illustrative, not measured</span>}
      </div>
    </div>,
    publicOgSize,
  );
}
