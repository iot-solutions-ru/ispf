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

function alignIcon(
  axis: "h" | "v",
  edge: "start" | "center" | "end",
  className?: string
) {
  const bars =
    axis === "h"
      ? [
          { x: 5, y: 3, w: 8, h: 1.5 },
          { x: 3, y: 7, w: 10, h: 1.5 },
          { x: 6, y: 11, w: 6, h: 1.5 },
        ]
      : [
          { x: 3, y: 5, w: 1.5, h: 8 },
          { x: 7, y: 3, w: 1.5, h: 10 },
          { x: 11, y: 6, w: 1.5, h: 6 },
        ];
  const guide =
    axis === "h"
      ? edge === "start"
        ? { x1: 2, y1: 3, x2: 2, y2: 13 }
        : edge === "center"
          ? { x1: 8, y1: 2, x2: 8, y2: 14 }
          : { x1: 14, y1: 3, x2: 14, y2: 13 }
      : edge === "start"
        ? { x1: 3, y1: 2, x2: 13, y2: 2 }
        : edge === "center"
          ? { x1: 2, y1: 8, x2: 14, y2: 8 }
          : { x1: 3, y1: 14, x2: 13, y2: 14 };
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <line x1={guide.x1} y1={guide.y1} x2={guide.x2} y2={guide.y2} stroke="var(--accent)" strokeWidth={2} strokeLinecap="round" />
      {bars.map((b, i) => (
        <rect key={i} x={b.x} y={b.y} width={b.w} height={b.h} rx={0.5} fill="currentColor" opacity={0.85} />
      ))}
    </svg>
  );
}

export function IconAlignLeft({ className }: IconProps) {
  return alignIcon("h", "start", className);
}
export function IconAlignCenterH({ className }: IconProps) {
  return alignIcon("h", "center", className);
}
export function IconAlignRight({ className }: IconProps) {
  return alignIcon("h", "end", className);
}
export function IconAlignTop({ className }: IconProps) {
  return alignIcon("v", "start", className);
}
export function IconAlignMiddleV({ className }: IconProps) {
  return alignIcon("v", "center", className);
}
export function IconAlignBottom({ className }: IconProps) {
  return alignIcon("v", "end", className);
}

export function IconDistributeH({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <rect x="2" y="6" width="3" height="4" rx="0.5" fill="currentColor" />
      <rect x="6.5" y="6" width="3" height="4" rx="0.5" fill="currentColor" />
      <rect x="11" y="6" width="3" height="4" rx="0.5" fill="currentColor" />
      <path d="M4.5 4.5v7M8 4.5v7M11.5 4.5v7" stroke="var(--accent)" strokeWidth="1.2" strokeDasharray="1.5 1" />
      <path d="M5.5 8h1M9 8h1" stroke="var(--accent)" strokeWidth="1.5" markerEnd="none" />
    </svg>
  );
}

export function IconDistributeV({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <rect x="6" y="2" width="4" height="3" rx="0.5" fill="currentColor" />
      <rect x="6" y="6.5" width="4" height="3" rx="0.5" fill="currentColor" />
      <rect x="6" y="11" width="4" height="3" rx="0.5" fill="currentColor" />
      <path d="M4.5 3.5h7M4.5 8h7M4.5 12.5h7" stroke="var(--accent)" strokeWidth="1.2" strokeDasharray="1.5 1" />
    </svg>
  );
}

export function IconFlipH({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M8 2v12" stroke="var(--accent)" strokeWidth="1.5" strokeDasharray="2 1.5" />
      <path d="M4 5 8 8 4 11" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M12 5 8 8 12 11" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" opacity={0.45} />
    </svg>
  );
}

export function IconFlipV({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M2 8h12" stroke="var(--accent)" strokeWidth="1.5" strokeDasharray="2 1.5" />
      <path d="M5 4 8 8 11 4" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M5 12 8 8 11 12" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" opacity={0.45} />
    </svg>
  );
}

export function IconRotateCw({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M8 3.5a4.5 4.5 0 1 1-3.2 7.6" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M5 3.5H8V6.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconRotateCcw({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M8 3.5a4.5 4.5 0 1 0 3.2 7.6" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M11 3.5H8V6.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconGrid({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M2 6h12M2 10h12M6 2v12M10 2v12" stroke="currentColor" strokeWidth="1.2" opacity={0.7} />
    </svg>
  );
}

export function IconSnapGrid({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 16 16" aria-hidden>
      <path d="M2 6h12M2 10h12M6 2v12M10 2v12" stroke="currentColor" strokeWidth="1.2" opacity={0.5} />
      <circle cx="12" cy="12" r="2.5" fill="none" stroke="var(--accent)" strokeWidth="1.3" />
      <path d="M12 10.5V8M10.5 12H8" stroke="var(--accent)" strokeWidth="1.2" strokeLinecap="round" />
    </svg>
  );
}
