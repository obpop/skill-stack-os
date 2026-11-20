"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { UniverseNode } from "@/lib/types";

type Props = {
  nodes: UniverseNode[];
  onSelect: (node: UniverseNode) => void;
  onSearch?: (query: string) => Promise<UniverseNode[]>;
};

export function SearchCommand({ nodes, onSelect, onSearch }: Props) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [persistedResults, setPersistedResults] = useState<UniverseNode[]>([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const requestSequence = useRef(0);
  const localResults = useMemo(() => {
    if (!query.trim()) return [];
    const normalized = query.toLowerCase();
    return nodes
      .filter((node) => node.name.toLowerCase().includes(normalized))
      .sort((a, b) => b.importance - a.importance)
      .slice(0, 7);
  }, [nodes, query]);
  const results = onSearch ? persistedResults : localResults;

  useEffect(() => {
    if (!onSearch) return;
    const normalized = query.trim();
    const sequence = ++requestSequence.current;
    setSearchError(null);
    if (!normalized) {
      setPersistedResults([]);
      setSearching(false);
      return;
    }
    setPersistedResults([]);
    setSearching(true);
    const timer = window.setTimeout(() => {
      void onSearch(normalized)
        .then((matches) => {
          if (requestSequence.current === sequence) setPersistedResults(matches.slice(0, 7));
        })
        .catch((problem) => {
          if (requestSequence.current !== sequence) return;
          setPersistedResults([]);
          setSearchError(problem instanceof Error ? problem.message : "Search is temporarily unavailable.");
        })
        .finally(() => {
          if (requestSequence.current === sequence) setSearching(false);
        });
    }, 180);
    return () => window.clearTimeout(timer);
  }, [onSearch, query]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "/" && document.activeElement?.tagName !== "INPUT") {
        event.preventDefault();
        inputRef.current?.focus();
      }
      if (event.key === "Escape") {
        setOpen(false);
        inputRef.current?.blur();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const choose = (node: UniverseNode) => {
    setQuery(node.name);
    setOpen(false);
    onSelect(node);
  };

  return (
    <div className="search-command">
      <span className="search-icon" aria-hidden="true">⌕</span>
      <input
        ref={inputRef}
        value={query}
        onChange={(event) => {
          setQuery(event.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(event) => {
          if (event.key === "Enter" && results[0]) choose(results[0]);
        }}
        aria-label="Search your Skill Stack"
        aria-expanded={open && results.length > 0}
        aria-controls="search-results"
        aria-busy={searching}
        placeholder="Search skills or projects"
      />
      <kbd>/</kbd>
      {open && results.length > 0 && (
        <div className="search-results" id="search-results" role="listbox">
          <div className="eyebrow search-eyebrow">Navigate to</div>
          {results.map((node) => (
            <button key={node.id} role="option" onClick={() => choose(node)}>
              <span>{node.name}</span>
              <small>{node.type.replaceAll("_", " ")} · {node.domain}</small>
            </button>
          ))}
        </div>
      )}
      {open && searching && <div className="search-status" role="status">Searching your universeâ€¦</div>}
      {open && searchError && <div className="search-status search-error" role="alert">{searchError}</div>}
    </div>
  );
}
