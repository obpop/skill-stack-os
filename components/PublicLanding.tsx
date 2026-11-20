"use client";

import { useState, type FormEvent } from "react";
import { Logo } from "@/components/site/Logo";

type Props = {
  authMode: "local" | "auth0";
  onLogin: (name: string, demo: boolean) => Promise<void>;
  onAuth0Login: () => void;
  busy: boolean;
  error: string | null;
};

/** Signed-out screen of the app: a short welcome and the sign-in card. */
export function PublicLanding({ authMode, onLogin, onAuth0Login, busy, error }: Props) {
  const [name, setName] = useState("");
  const submit = (event: FormEvent) => { event.preventDefault(); void onLogin(name, false); };

  return <main className="ss-page">
    <div className="ss-container">
      <header className="ss-topbar">
        <span className="ss-wordmark"><Logo />Skill Stack</span>
      </header>

      <section id="start" className="ss-slab ss-start" aria-labelledby="start-title">
        <div>
          <span className="ss-eyebrow ss-jade">Welcome</span>
          <h1 id="start-title">Know the next skill worth building.</h1>
          <p>Add the skills you have. Skill Stack finds the one your stack is missing and explains why, what it costs, and the first step.</p>
          <p>Everything stays private on this machine. Publishing is a separate preview-and-consent step.</p>
        </div>
        <div className="ss-auth">
          {authMode === "auth0" ? <>
            <button className="ss-btn ss-btn-primary" disabled={busy} onClick={onAuth0Login}>{busy ? "Opening..." : "Continue with Auth0"}</button>
            <small>Your Auth0 client secret remains on the backend.</small>
          </> : <>
            <form onSubmit={submit}>
              <label htmlFor="landing-name">How should we address you?</label>
              <input id="landing-name" maxLength={120} value={name} onChange={(event) => setName(event.target.value)} placeholder="Your name" />
              <button className="ss-btn ss-btn-primary" disabled={busy}>{busy ? "Opening..." : "Build my Skill Stack"}</button>
            </form>
            <button className="ss-text-button" disabled={busy} onClick={() => void onLogin("Olli", true)}>Explore the existing demo data</button>
            <small>Local development uses a signed bearer token. Nothing leaves your machine.</small>
          </>}
          {error && <p className="inline-error ss-error" role="alert">{error}</p>}
        </div>
      </section>

      <section className="ss-slab ss-start" aria-labelledby="ai-title">
        <div>
          <span className="ss-eyebrow">Optional</span>
          <h2 id="ai-title">Works without AI</h2>
        </div>
        <p>The core recommendations come from a deterministic ranker and need no key. To turn on AI analysis, put your OpenAI key in <code>backend/.env</code> as <code>OPENAI_API_KEY_SKILL_STACK</code>, set <code>AI_ENABLED=true</code>, <code>AI_PROVIDER=openai</code> and budgets above 0, then restart the backend. The budgets cap what it can spend.</p>
      </section>
    </div>
  </main>;
}
