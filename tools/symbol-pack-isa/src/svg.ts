/** Original ISA/ISO-style P&ID primitives (64×64 canvas). Not traced from vendor artwork. */

const S = "var(--border)";
const F = "var(--bg-elevated)";
const A = "var(--accent)";
const W = 2;

export function line(x1: number, y1: number, x2: number, y2: number, dash?: string): string {
  const d = dash ? ` stroke-dasharray="${dash}"` : "";
  return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${S}" stroke-width="${W}"${d}/>`;
}

export function rect(x: number, y: number, w: number, h: number, rx = 0): string {
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${F}" stroke="${S}" stroke-width="${W}"/>`;
}

export function circle(cx: number, cy: number, r: number, fill = F): string {
  return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}" stroke="${S}" stroke-width="${W}"/>`;
}

export function poly(points: string, fill = F): string {
  return `<polygon points="${points}" fill="${fill}" stroke="${S}" stroke-width="${W}"/>`;
}

export function path(d: string, fill: string | "none" = F): string {
  return `<path d="${d}" fill="${fill}" stroke="${S}" stroke-width="${W}"/>`;
}

export function text(x: number, y: number, label: string, size = 11): string {
  return `<text x="${x}" y="${y}" text-anchor="middle" dominant-baseline="middle" fill="${S}" font-size="${size}" font-family="system-ui,sans-serif" font-weight="600">${label}</text>`;
}

export function pipeHorizontal(y = 32): string {
  return line(0, y, 64, y);
}

export function pipeVertical(x = 32): string {
  return line(x, 0, x, 64);
}

export function bubble(cx: number, cy: number, r: number, label: string): string {
  return `${circle(cx, cy, r)}${text(cx, cy + 1, label, 10)}`;
}

export function instrumentBubble(label: string): string {
  return bubble(32, 32, 14, label);
}

export function portsHorizontal(y = 32): { id: string; x: number; y: number }[] {
  return [
    { id: "w", x: 0, y },
    { id: "e", x: 64, y },
  ];
}

export function portsVertical(x = 32): { id: string; x: number; y: number }[] {
  return [
    { id: "n", x, y: 0 },
    { id: "s", x, y: 64 },
  ];
}

export function portsCross(): { id: string; x: number; y: number }[] {
  return [
    { id: "n", x: 32, y: 0 },
    { id: "s", x: 32, y: 64 },
    { id: "w", x: 0, y: 32 },
    { id: "e", x: 64, y: 32 },
  ];
}

export function wrap(content: string): string {
  return `<g>${content}</g>`;
}

export { A as accentFill };
