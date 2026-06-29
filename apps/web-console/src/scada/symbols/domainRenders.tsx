import type { ComponentType } from "react";
import type { SymbolRenderProps } from "../../types/scadaMimic";
import { asBool, asNum } from "../utils";
import { selectionOutline } from "./process";

export function DomainGlyphSymbol({ width, height, values, props, selected }: SymbolRenderProps) {
  const glyph = String(props.glyph ?? "EQ");
  const active = asBool(values.running ?? values.active ?? values.open ?? values.closed);
  const stroke = active ? "#3fb950" : "#484f58";
  return (
    <g>
      <rect width={width} height={height} rx={5} fill="var(--bg-elevated)" stroke={stroke} strokeWidth={2} />
      <text x={width / 2} y={height / 2 + (glyph.length > 3 ? 2 : 4)} textAnchor="middle" fontSize={glyph.length > 3 ? 8 : 11} fontWeight={700} fill="var(--text)">{glyph}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainDuctSymbol({ width, height, selected }: SymbolRenderProps) {
  const cy = height / 2;
  return (
    <g>
      <rect x={2} y={cy - 8} width={width - 4} height={16} rx={8} fill="var(--bg-elevated)" stroke="#58a6ff" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainRoundDuctSymbol({ width, height, selected }: SymbolRenderProps) {
  const cy = height / 2;
  return (
    <g>
      <ellipse cx={width / 2} cy={cy} rx={width / 2 - 4} ry={height / 2 - 4} fill="var(--bg-elevated)" stroke="#58a6ff" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainFlexPipeSymbol({ width, height, selected }: SymbolRenderProps) {
  const cy = height / 2;
  return (
    <g>
      <path d={`M0,${cy} Q${width * 0.25},${cy - 10} ${width * 0.5},${cy} T${width},${cy}`} fill="none" stroke="#58a6ff" strokeWidth={3} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainSegPipeSymbol({ width, height, selected }: SymbolRenderProps) {
  const cy = height / 2;
  return (
    <g>
      <line x1={0} y1={cy} x2={width} y2={cy} stroke="#58a6ff" strokeWidth={4} strokeDasharray="12 6" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainWireSymbol({ width, height, selected }: SymbolRenderProps) {
  const cy = height / 2;
  return (
    <g>
      <line x1={0} y1={cy} x2={width} y2={cy} stroke="#d29922" strokeWidth={2} />
      <line x1={0} y1={cy - 4} x2={width} y2={cy - 4} stroke="#d29922" strokeWidth={1} strokeDasharray="2 4" />
      <line x1={0} y1={cy + 4} x2={width} y2={cy + 4} stroke="#d29922" strokeWidth={1} strokeDasharray="2 4" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainStructureSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <polygon points={`${width / 2},4 ${width - 4},${height - 4} 4,${height - 4}`} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      <rect x={width * 0.35} y={height * 0.45} width={width * 0.3} height={height * 0.35} fill="none" stroke="var(--border)" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainVehicleSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  return (
    <g>
      <rect x={8} y={height * 0.35} width={width - 16} height={height * 0.35} rx={4} fill="var(--bg-elevated)" stroke={running ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <circle cx={width * 0.28} cy={height * 0.75} r={5} fill="var(--text)" />
      <circle cx={width * 0.72} cy={height * 0.75} r={5} fill="var(--text)" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainWellheadSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <line x1={width / 2} y1={height - 4} x2={width / 2} y2={height * 0.4} stroke="#f0883e" strokeWidth={3} />
      <rect x={width * 0.2} y={height * 0.15} width={width * 0.6} height={height * 0.3} rx={2} fill="var(--bg-elevated)" stroke={active ? "#f0883e" : "#484f58"} strokeWidth={2} />
      <line x1={width * 0.2} y1={height * 0.3} x2={width * 0.8} y2={height * 0.3} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainLabSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  return (
    <g>
      <path d={`M${cx - 10},${height - 4} L${cx - 4},${height * 0.35} L${cx + 4},${height * 0.35} L${cx + 10},${height - 4} Z`} fill="var(--bg-elevated)" stroke="#a371f7" strokeWidth={2} />
      <ellipse cx={cx} cy={height * 0.28} rx={12} ry={4} fill="none" stroke="#a371f7" strokeWidth={1.5} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainGaugeSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const value = asNum(values.value);
  const cx = width / 2;
  const cy = height / 2;
  const r = Math.min(width, height) / 2 - 2;
  const angle = ((value ?? 50) / 100) * 180 - 90;
  const nx = cx + r * 0.7 * Math.cos((angle * Math.PI) / 180);
  const ny = cy + r * 0.7 * Math.sin((angle * Math.PI) / 180);
  return (
    <g>
      <path d={`M${cx - r},${cy} A${r},${r} 0 0 1 ${cx + r},${cy}`} fill="none" stroke="var(--border)" strokeWidth={2} />
      <line x1={cx} y1={cy} x2={nx} y2={ny} stroke="#f0883e" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainChuteSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <polygon points={`8,8 ${width - 8},8 ${width - 4},${height - 4} 4,${height - 4}`} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainScreenSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="#0d1117" stroke={active ? "#58a6ff" : "#484f58"} strokeWidth={2} />
      <rect x={6} y={6} width={width - 12} height={height - 16} fill="#161b22" stroke="var(--border)" />
      <rect x={width / 2 - 12} y={height - 8} width={24} height={4} rx={1} fill="var(--border)" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainTriangleSymbol({ width, height, styleOverrides, selected }: SymbolRenderProps) {
  return (
    <g>
      <polygon points={`${width / 2},4 ${width - 4},${height - 4} 4,${height - 4}`} fill={styleOverrides.fill ?? "transparent"} stroke={styleOverrides.stroke ?? "var(--border)"} strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DomainHexagonSymbol({ width, height, styleOverrides, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  const r = Math.min(width, height) / 2 - 2;
  const pts = Array.from({ length: 6 }, (_, i) => {
    const a = ((60 * i - 30) * Math.PI) / 180;
    return `${cx + r * Math.cos(a)},${cy + r * Math.sin(a)}`;
  }).join(" ");
  return (
    <g>
      <polygon points={pts} fill={styleOverrides.fill ?? "transparent"} stroke={styleOverrides.stroke ?? "var(--border)"} strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export const DOMAIN_RENDER_KINDS = {
  glyph: DomainGlyphSymbol,
  duct: DomainDuctSymbol,
  "duct-round": DomainRoundDuctSymbol,
  "flex-pipe": DomainFlexPipeSymbol,
  "seg-pipe": DomainSegPipeSymbol,
  wire: DomainWireSymbol,
  structure: DomainStructureSymbol,
  vehicle: DomainVehicleSymbol,
  wellhead: DomainWellheadSymbol,
  lab: DomainLabSymbol,
  gauge: DomainGaugeSymbol,
  chute: DomainChuteSymbol,
  screen: DomainScreenSymbol,
  triangle: DomainTriangleSymbol,
  hexagon: DomainHexagonSymbol,
} as const;

export type DomainRenderKind = keyof typeof DOMAIN_RENDER_KINDS;

export function domainRenderer(kind: DomainRenderKind): ComponentType<SymbolRenderProps> {
  return DOMAIN_RENDER_KINDS[kind];
}
