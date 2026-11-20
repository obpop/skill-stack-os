/** The Skill Stack mark: three rounded slabs, wider and brighter toward the base. */
export function Logo({ className = "ss-logo" }: { className?: string }) {
  return <svg className={className} viewBox="-1 -1 24 21" aria-hidden="true" focusable="false">
    <rect x="5" y="0" width="14" height="5" rx="2.5" fill="#587c6f" />
    <rect x="2" y="7" width="18" height="5" rx="2.5" fill="#78a694" />
    <rect x="0" y="14" width="22" height="5" rx="2.5" fill="#99d0ba" />
  </svg>;
}
