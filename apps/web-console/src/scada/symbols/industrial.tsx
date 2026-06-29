import type { SymbolRenderProps } from "../../types/scadaMimic";
import { asBool, asNum, fmtNum } from "../utils";
import { selectionOutline } from "./process";

function stroke(values: Record<string, unknown>, key: string, on: string, off = "#484f58") {
  return asBool(values[key]) ? on : off;
}

export function ValveControlSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const open = asBool(values.open);
  const cx = width / 2;
  return (
    <g>
      <line x1={cx} y1={0} x2={cx} y2={height * 0.35} stroke="var(--text)" strokeWidth={2} />
      <polygon points={`${cx},4 ${cx + 10},14 ${cx - 10},14`} fill="var(--bg-elevated)" stroke="var(--text)" />
      <line x1={cx} y1={height * 0.35} x2={cx} y2={height * 0.55} stroke="var(--text)" strokeWidth={2} />
      <circle cx={cx} cy={height * 0.68} r={10} fill={open ? "#3fb950" : "#484f58"} stroke="var(--text)" />
      <line x1={cx} y1={height * 0.78} x2={cx} y2={height} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ValveSafetySymbol({ width, height, values, selected }: SymbolRenderProps) {
  const open = asBool(values.open);
  const cx = width / 2;
  return (
    <g>
      <line x1={cx} y1={0} x2={cx} y2={height - 14} stroke="var(--text)" strokeWidth={2} />
      <polygon points={`${cx},${height - 14} ${cx + 12},${height} ${cx - 12},${height}`} fill={open ? "#f0883e" : "#484f58"} stroke="var(--text)" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ValveNeedleSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const open = asBool(values.open);
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <line x1={cx} y1={0} x2={cx} y2={height} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 10} y1={cy} x2={cx + 10} y2={cy} stroke={open ? "#3fb950" : "#484f58"} strokeWidth={3} />
      <line x1={cx} y1={cy - 10} x2={cx} y2={cy + 10} stroke={open ? "#3fb950" : "#484f58"} strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ValveDiaphragmSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const open = asBool(values.open);
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <line x1={cx} y1={0} x2={cx} y2={cy - 12} stroke="var(--text)" strokeWidth={2} />
      <path d={`M${cx - 14},${cy} Q${cx},${cy - 10} ${cx + 14},${cy} T${cx - 14},${cy}`} fill="none" stroke={open ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <line x1={cx} y1={cy + 12} x2={cx} y2={height} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PipeElbowSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <path d={`M4,${height - 4} L4,${height / 2} Q4,4 ${width - 4},4`} fill="none" stroke="#58a6ff" strokeWidth={4} strokeLinecap="round" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PipeReducerSymbol({ width, height, selected }: SymbolRenderProps) {
  const cy = height / 2;
  return (
    <g>
      <polygon points={`4,${cy - 8} ${width - 4},${cy - 4} ${width - 4},${cy + 4} 4,${cy + 8}`} fill="none" stroke="#58a6ff" strokeWidth={3} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function FanBlowerSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  const cx = width / 2;
  const cy = height / 2;
  const c = stroke(values, "running", "#3fb950");
  return (
    <g>
      <circle cx={cx} cy={cy} r={Math.min(width, height) / 2 - 2} fill="var(--bg-elevated)" stroke={c} strokeWidth={2} />
      {[0, 60, 120, 180, 240, 300].map((deg) => (
        <line key={deg} x1={cx} y1={cy} x2={cx + 10 * Math.cos((deg * Math.PI) / 180)} y2={cy + 10 * Math.sin((deg * Math.PI) / 180)} stroke={running ? "#58a6ff" : "#484f58"} strokeWidth={2} />
      ))}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ColumnDistillationSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  return (
    <g>
      <rect x={cx - 14} y={8} width={28} height={height - 16} rx={4} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      {[0.25, 0.45, 0.65].map((y) => (
        <line key={y} x1={cx - 10} y1={height * y} x2={cx + 10} y2={height * y} stroke="#58a6ff" strokeWidth={1} />
      ))}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function BoilerSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  return (
    <g>
      <rect x={4} y={height * 0.25} width={width - 8} height={height * 0.55} rx={6} fill="var(--bg-elevated)" stroke={running ? "#f0883e" : "#484f58"} strokeWidth={2} />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fontSize={11} fill="var(--text)">B</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function FurnaceSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <rect x={8} y={12} width={width - 16} height={height - 20} rx={4} fill="var(--bg-elevated)" stroke={active ? "#f85149" : "#484f58"} strokeWidth={2} />
      <polygon points={`${width / 2},${height - 8} ${width / 2 + 8},${height - 18} ${width / 2 - 8},${height - 18}`} fill={active ? "#f0883e" : "#484f58"} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function SteamTurbineSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <circle cx={cx} cy={cy} r={Math.min(width, height) / 2 - 4} fill="var(--bg-elevated)" stroke={running ? "#58a6ff" : "#484f58"} strokeWidth={2} />
      <text x={cx} y={cy + 4} textAnchor="middle" fontSize={10} fill="var(--text)">T</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function SiloSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const level = asNum(values.fillLevel);
  const cx = width / 2;
  return (
    <g>
      <polygon points={`${cx},8 ${width - 6},${height - 8} 6,${height - 8}`} fill="var(--bg-elevated)" stroke="#58a6ff" strokeWidth={2} />
      <text x={cx} y={height - 2} textAnchor="middle" fontSize={9} fill="var(--text-muted)">{fmtNum(level, 0)}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function HopperSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  return (
    <g>
      <rect x={6} y={6} width={width - 12} height={height * 0.45} rx={2} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      <polygon points={`${cx},${height * 0.45} ${width - 8},${height - 6} 8,${height - 6}`} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ConveyorSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  return (
    <g>
      <line x1={4} y1={height / 2} x2={width - 4} y2={height / 2} stroke={running ? "#58a6ff" : "#484f58"} strokeWidth={4} />
      <polygon points={`${width - 8},${height / 2 - 6} ${width - 2},${height / 2} ${width - 8},${height / 2 + 6}`} fill={running ? "#58a6ff" : "#484f58"} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function AgitatorSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  const cx = width / 2;
  return (
    <g>
      <rect x={cx - 16} y={height * 0.35} width={32} height={height * 0.55} rx={4} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      <line x1={cx} y1={4} x2={cx} y2={height * 0.35} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 10} y1={height * 0.5} x2={cx + 10} y2={height * 0.5} stroke={running ? "#3fb950" : "#484f58"} strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function OrificeSymbol({ width, height, selected }: SymbolRenderProps) {
  const cy = height / 2;
  return (
    <g>
      <line x1={0} y1={cy} x2={width * 0.35} y2={cy} stroke="#58a6ff" strokeWidth={3} />
      <polygon points={`${width * 0.35},${cy - 8} ${width * 0.65},${cy} ${width * 0.35},${cy + 8}`} fill="var(--bg-elevated)" stroke="var(--text)" />
      <line x1={width * 0.65} y1={cy} x2={width} y2={cy} stroke="#58a6ff" strokeWidth={3} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function SteamTrapSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const open = asBool(values.open);
  const cx = width / 2;
  return (
    <g>
      <line x1={cx} y1={0} x2={cx} y2={height * 0.35} stroke="var(--text)" strokeWidth={2} />
      <rect x={cx - 10} y={height * 0.35} width={20} height={14} rx={2} fill={open ? "#58a6ff" : "#484f58"} stroke="var(--text)" />
      <line x1={cx} y1={height * 0.49} x2={cx} y2={height} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function VentSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  return (
    <g>
      <line x1={cx} y1={height - 4} x2={cx} y2={height / 2} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 10} y1={height / 2} x2={cx + 10} y2={height / 2} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 6} y1={height / 2 - 8} x2={cx + 6} y2={height / 2 - 8} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function BlindFlangeSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <line x1={0} y1={cy} x2={cx - 6} y2={cy} stroke="#58a6ff" strokeWidth={3} />
      <line x1={cx - 6} y1={cy - 10} x2={cx - 6} y2={cy + 10} stroke="var(--text)" strokeWidth={3} />
      <line x1={cx + 6} y1={cy - 10} x2={cx + 6} y2={cy + 10} stroke="var(--text)" strokeWidth={3} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ReactorVesselSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  const cx = width / 2;
  return (
    <g>
      <ellipse cx={cx} cy={height / 2} rx={width / 2 - 4} ry={height / 2 - 6} fill="var(--bg-elevated)" stroke={running ? "#a371f7" : "#484f58"} strokeWidth={2} />
      <text x={cx} y={height / 2 + 4} textAnchor="middle" fontSize={10} fill="var(--text)">R</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DamperSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const open = asBool(values.open);
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <rect x={4} y={cy - 6} width={width - 8} height={12} fill="none" stroke="var(--border)" strokeWidth={2} />
      <line x1={cx - 12} y1={cy} x2={cx + 12} y2={cy} stroke={open ? "#3fb950" : "#484f58"} strokeWidth={3} transform={`rotate(${open ? 0 : 45} ${cx} ${cy})`} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function TankConicalSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const level = asNum(values.fillLevel);
  const cx = width / 2;
  return (
    <g>
      <rect x={cx - 18} y={8} width={36} height={height * 0.55} fill="var(--bg-elevated)" stroke="#58a6ff" strokeWidth={2} />
      <polygon points={`${cx - 18},${height * 0.63} ${cx},${height - 8} ${cx + 18},${height * 0.63}`} fill="var(--bg-elevated)" stroke="#58a6ff" strokeWidth={2} />
      <text x={cx} y={height - 2} textAnchor="middle" fontSize={9} fill="var(--text-muted)">{fmtNum(level, 0)}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function LevelGaugeSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const level = asNum(values.value);
  const cx = width / 2;
  return (
    <g>
      <rect x={cx - 6} y={4} width={12} height={height - 8} fill="var(--bg)" stroke="var(--border)" strokeWidth={1.5} />
      <rect x={cx - 4} y={height - 8 - (level ?? 30) * 0.01 * (height - 16)} width={8} height={(level ?? 30) * 0.01 * (height - 16)} fill="#238636" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function RelaySymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  const cx = width / 2;
  return (
    <g>
      <rect x={cx - 14} y={8} width={28} height={20} rx={2} fill="var(--bg-elevated)" stroke={active ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <line x1={cx} y1={28} x2={cx} y2={height - 8} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 8} y1={height - 8} x2={cx + 8} y2={height - 8} stroke={active ? "#3fb950" : "var(--text)"} strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ManualSwitchSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const closed = asBool(values.closed);
  const cx = width / 2;
  return (
    <g>
      <line x1={cx} y1={0} x2={cx} y2={height / 3} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx} y1={(2 * height) / 3} x2={cx} y2={height} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 10} y1={height / 2} x2={cx + (closed ? 10 : -2)} y2={height / 2 - (closed ? 0 : 10)} stroke={closed ? "#3fb950" : "#f0883e"} strokeWidth={2.5} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PilotLampSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <circle cx={cx} cy={cy} r={Math.min(width, height) / 2 - 2} fill={active ? "#3fb950" : "#484f58"} stroke="var(--text)" strokeWidth={2} opacity={active ? 1 : 0.6} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function AmmeterSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <circle cx={cx} cy={cy} r={Math.min(width, height) / 2 - 2} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      <text x={cx} y={cy + 4} textAnchor="middle" fontSize={11} fill="var(--text)">A</text>
      <text x={cx} y={height - 2} textAnchor="middle" fontSize={8} fill="var(--text-muted)">{fmtNum(values.value, 1)}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function VoltmeterSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <circle cx={cx} cy={cy} r={Math.min(width, height) / 2 - 2} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      <text x={cx} y={cy + 4} textAnchor="middle" fontSize={11} fill="var(--text)">V</text>
      <text x={cx} y={height - 2} textAnchor="middle" fontSize={8} fill="var(--text-muted)">{fmtNum(values.value, 0)}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function CurrentTransformerSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <circle cx={cx - 8} cy={cy} r={10} fill="none" stroke="#58a6ff" strokeWidth={2} />
      <circle cx={cx + 8} cy={cy} r={10} fill="none" stroke="#58a6ff" strokeWidth={2} />
      <text x={cx} y={cy + 4} textAnchor="middle" fontSize={8} fill="var(--text-muted)">CT</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PotentialTransformerSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <circle cx={cx - 8} cy={cy} r={10} fill="none" stroke="#58a6ff" strokeWidth={2} />
      <circle cx={cx + 8} cy={cy} r={10} fill="none" stroke="#58a6ff" strokeWidth={2} />
      <text x={cx} y={cy + 4} textAnchor="middle" fontSize={8} fill="var(--text-muted)">PT</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ArresterSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  return (
    <g>
      <line x1={cx} y1={0} x2={cx} y2={height * 0.4} stroke="var(--text)" strokeWidth={2} />
      <polygon points={`${cx},${height * 0.4} ${cx + 10},${height * 0.55} ${cx - 10},${height * 0.55}`} fill="#d29922" stroke="var(--text)" />
      <line x1={cx} y1={height * 0.55} x2={cx} y2={height - 8} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 10} y1={height - 8} x2={cx + 10} y2={height - 8} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function BatterySymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <rect x={6} y={height * 0.25} width={width - 20} height={height * 0.5} rx={2} fill="var(--bg-elevated)" stroke={active ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <rect x={width - 12} y={height * 0.35} width={6} height={height * 0.3} fill={active ? "#3fb950" : "#484f58"} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function SolarPanelSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <rect x={4} y={8} width={width - 8} height={height - 16} rx={2} fill={active ? "#1f6feb" : "var(--bg-elevated)"} stroke="#58a6ff" strokeWidth={2} opacity={active ? 0.85 : 1} />
      <line x1={width / 3} y1={8} x2={width / 3} y2={height - 8} stroke="var(--border)" />
      <line x1={(2 * width) / 3} y1={8} x2={(2 * width) / 3} y2={height - 8} stroke="var(--border)" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function WindTurbineSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  const cx = width / 2;
  return (
    <g>
      <line x1={cx} y1={height - 4} x2={cx} y2={height / 2} stroke="var(--text)" strokeWidth={2} />
      <circle cx={cx} cy={height / 2} r={4} fill="var(--text)" />
      {[0, 120, 240].map((deg) => (
        <line key={deg} x1={cx} y1={height / 2} x2={cx + 14 * Math.cos(((deg - 90) * Math.PI) / 180)} y2={height / 2 + 14 * Math.sin(((deg - 90) * Math.PI) / 180)} stroke={running ? "#58a6ff" : "#484f58"} strokeWidth={2} />
      ))}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function UpsSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg-elevated)" stroke={active ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fontSize={10} fill="var(--text)">UPS</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function MotorStarterSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg-elevated)" stroke={running ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fontSize={9} fill="var(--text)">MCC</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PlcSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <rect width={width} height={height} rx={3} fill="var(--bg-elevated)" stroke={active ? "#58a6ff" : "#484f58"} strokeWidth={2} />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fontSize={10} fill="var(--text)">PLC</text>
      {[0.3, 0.5, 0.7].map((x) => (
        <circle key={x} cx={width * x} cy={height * 0.75} r={2} fill={active ? "#3fb950" : "#484f58"} />
      ))}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function EarthingSwitchSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const closed = asBool(values.closed);
  const cx = width / 2;
  return (
    <g>
      <line x1={cx} y1={0} x2={cx} y2={height / 3} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 10} y1={height / 2} x2={cx + (closed ? 10 : 4)} y2={height / 2 + (closed ? 0 : 8)} stroke={closed ? "#3fb950" : "#f0883e"} strokeWidth={2.5} />
      <line x1={cx} y1={(2 * height) / 3} x2={cx} y2={height - 10} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 8} y1={height - 6} x2={cx + 8} y2={height - 6} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function LineHorizontalSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <line x1={0} y1={height / 2} x2={width} y2={height / 2} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function LineVerticalSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <line x1={width / 2} y1={0} x2={width / 2} y2={height} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function FrameSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <rect x={2} y={2} width={width - 4} height={height - 4} rx={4} fill="none" stroke="var(--border)" strokeWidth={2} strokeDasharray="6 4" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ConnectorSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <circle cx={cx} cy={cy} r={5} fill="#58a6ff" stroke="var(--bg)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function LegendBoxSymbol({ width, height, props, selected }: SymbolRenderProps) {
  const title = String(props.title ?? "Legend");
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={1.5} />
      <text x={8} y={16} fill="var(--text)" fontSize={11} fontWeight={600}>{title}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function CalloutSymbol({ width, height, props, selected }: SymbolRenderProps) {
  const text = String(props.text ?? "Note");
  return (
    <g>
      <polygon points={`8,4 ${width - 4},4 ${width - 4},${height - 12} ${width / 2},${height - 12} ${width / 2 - 8},${height - 4} 8,${height - 4}`} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={1.5} />
      <text x={12} y={height / 2} fill="var(--text-muted)" fontSize={10}>{text}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}
