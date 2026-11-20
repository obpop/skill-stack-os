import type { PublicProfileView as PublicProfile } from "@/lib/api/types";

function pretty(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/^./, (letter) => letter.toUpperCase());
}

export function PublicProfileView({ profile, demo = false }: { profile: PublicProfile; demo?: boolean }) {
  return (
    <main className={`public-profile public-theme-${profile.theme.toLowerCase()}`}>
      <nav className="public-nav" aria-label="Public Skill Stack navigation">
        <a className="brand" href="/"><span className="brand-mark"><i /><i /><i /></span><span>Skill Stack</span></a>
        <div><a className="public-nav-cta" href="/">Build yours</a></div>
      </nav>

      <article className="public-profile-content">
        <header className="public-profile-hero">
          <div className="eyebrow">{demo ? "Fictional, privacy-safe demonstration" : "A selective public Skill Stack"}</div>
          <h1>{profile.displayName ? `${profile.displayName}'s Skill Stack` : "A Skill Stack in progress"}</h1>
          {profile.sentence && <p className="public-lede">{profile.sentence}</p>}
          <div className="public-skill-row" aria-label="Selected public skills">
            {profile.skills.map((skill) => <span key={skill.name}><strong>{skill.name}</strong>{skill.level && <small>{pretty(skill.level)}{skill.projectBacked ? " - linked project evidence" : " - self-assessed"}</small>}</span>)}
          </div>
          {profile.illustrativeRarity && <aside className="public-rarity"><strong>{profile.illustrativeRarity} illustrative</strong><span>{profile.rarityExplanation}</span></aside>}
        </header>

        {profile.developmentFocus && <section className="public-focus" aria-labelledby="development-focus-title">
          <div className="eyebrow">What to build next</div>
          <h2 id="development-focus-title">{profile.developmentFocus.name}</h2>
          <p>{profile.developmentFocus.whyNow}</p>
          <div className="public-action-grid"><article><span>Start here</span><p>{profile.developmentFocus.firstAction}</p></article><article><span>Make it visible</span><p>{profile.developmentFocus.evidenceOutcome}</p></article></div>
        </section>}

        {profile.intersections.length > 0 && <section className="public-section" aria-labelledby="combinations-title">
          <div className="eyebrow">How the combination works</div><h2 id="combinations-title">Useful intersections, explained</h2>
          <div className="public-card-grid">{profile.intersections.map((item) => <article key={item.name}><h3>{item.name}</h3><p>{item.explanation}</p><dl><div><dt>Tradeoff</dt><dd>{item.tradeoff}</dd></div><div><dt>First action</dt><dd>{item.firstAction}</dd></div></dl></article>)}</div>
        </section>}

        {profile.skills.length > 0 && <details className="public-visual" open>
          <summary>Optional visual stack</summary>
          <p>The text above is the complete explanation. This small map is only another way to see the selected public skills.</p>
          <div className="public-constellation" aria-hidden="true">
            <span className="constellation-center">What this stack can build</span>
            {profile.skills.map((skill, index) => <span key={skill.name} className={`constellation-skill constellation-skill-${index + 1}`}>{skill.name}</span>)}
          </div>
        </details>}

        {profile.projects.length > 0 && <section className="public-section" aria-labelledby="projects-title">
          <div className="eyebrow">Projects and proof</div><h2 id="projects-title">Evidence that can be inspected</h2>
          <div className="public-project-list">{profile.projects.map((project) => <article key={project.title}><div><span className="public-stage">{pretty(project.stage)}</span><h3>{project.title}</h3><p>{project.description}</p>{project.skillsApplied.length > 0 && <p className="public-applied">Skills applied: {project.skillsApplied.join(", ")}</p>}</div><div className="public-proof-list">{project.proof.map((proof) => <div key={proof.title}><strong>{proof.title}</strong><small>{pretty(proof.type)}</small>{proof.description && <p>{proof.description}</p>}{proof.url && <a href={proof.url} rel="noreferrer">Open evidence</a>}</div>)}{project.repositoryUrl && <a href={project.repositoryUrl} rel="noreferrer">Source repository</a>}{project.liveUrl && <a href={project.liveUrl} rel="noreferrer">Public demo</a>}</div></article>)}</div>
        </section>}

        {profile.timeline.length > 0 && <section className="public-section" aria-labelledby="timeline-title">
          <div className="eyebrow">Development over time</div><h2 id="timeline-title">A factual progress timeline</h2>
          <ol className="public-timeline">{profile.timeline.map((event, index) => <li key={`${event.recordedAt}-${index}`}><time dateTime={event.occurredOn ?? event.recordedAt}>{event.occurredOn ?? new Date(event.recordedAt).toISOString().slice(0, 10)}</time><div><h3>{event.title}</h3>{event.description && <p>{event.description}</p>}<small>{event.dateSource === "USER_ENTERED" ? "Date entered by the profile owner" : "Event time recorded by Skill Stack"}</small></div></li>)}</ol>
        </section>}

        <aside className="public-limitations" aria-labelledby="limitations-title"><div className="eyebrow">Truth boundaries</div><h2 id="limitations-title">What this page does not claim</h2><ul>{profile.limitations.map((item) => <li key={item}>{item}</li>)}</ul></aside>
      </article>
      <footer className="public-footer"><span>{profile.attribution}</span></footer>
    </main>
  );
}
