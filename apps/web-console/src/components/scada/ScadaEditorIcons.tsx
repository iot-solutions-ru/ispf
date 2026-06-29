type IconProps = { className?: string };

export function IconSelect({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M3 2.5 6.5 9H9.5L13 2.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M6 12.5h4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

export function IconPlace({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <rect x="3" y="3" width="10" height="10" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.5" />
      <path d="M8 6v4M6 8h4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

export function IconConnect({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <circle cx="4" cy="8" r="2" fill="none" stroke="currentColor" strokeWidth="1.5" />
      <circle cx="12" cy="8" r="2" fill="none" stroke="currentColor" strokeWidth="1.5" />
      <path d="M6 8h4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

export function IconUndo({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M3.5 8a4.5 4.5 0 0 1 7.8-3.1" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M3 5.5V8h2.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconRedo({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M12.5 8a4.5 4.5 0 0 0-7.8-3.1" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M13 5.5V8h-2.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconTrash({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M3.5 4.5h9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M6 4.5V3.5h4v1" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M5 4.5l.5 8h5l.5-8" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  );
}

export function IconSearch({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <circle cx="7" cy="7" r="4" fill="none" stroke="currentColor" strokeWidth="1.5" />
      <path d="m10 10 3 3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}
