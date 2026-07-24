import type { FlowNode } from "../../bpmn/model/types";

const FILL = "var(--bg-elevated)";
const STROKE = "currentColor";

/**
 * Classic BPMN 2.0 glyphs aligned with common notation guides
 * (e.g. Comindware BPMN 2.0 element reference).
 */
export default function BpmnNodeGlyph({ node }: { node: FlowNode }) {
  const w = node.width;
  const h = node.height;

  switch (node.type) {
    case "startEvent":
      return <EventCircle w={w} h={h} variant="start" marker={inferStartMarker(node)} throwing={false} />;
    case "endEvent":
      return <EventCircle w={w} h={h} variant="end" marker={inferEndMarker(node)} throwing />;
    case "intermediateCatchEvent":
    case "boundaryEvent":
      return (
        <EventCircle
          w={w}
          h={h}
          variant="intermediate"
          marker={node.catchKind ?? "none"}
          throwing={false}
        />
      );
    case "intermediateThrowEvent":
      return <EventCircle w={w} h={h} variant="intermediate" marker="message" throwing />;
    case "exclusiveGateway":
      return <Gateway w={w} h={h} kind="exclusive" />;
    case "parallelGateway":
      return <Gateway w={w} h={h} kind="parallel" />;
    case "serviceTask":
      return <Activity w={w} h={h} kind="service" />;
    case "userTask":
      return <Activity w={w} h={h} kind="user" />;
    case "messageTask":
      return <Activity w={w} h={h} kind="send" />;
    case "callActivity":
      return <Activity w={w} h={h} kind="call" />;
    case "subProcess":
      return <Activity w={w} h={h} kind="subprocess" />;
    default:
      return <Activity w={w} h={h} kind="abstract" />;
  }
}

type EventMarker = "none" | "timer" | "signal" | "message";

function inferStartMarker(node: FlowNode): EventMarker {
  if (node.ispf.durationSeconds) return "timer";
  if (node.ispf.signal) return "signal";
  if (node.ispf.message) return "message";
  return "none";
}

function inferEndMarker(node: FlowNode): EventMarker {
  if (node.ispf.message) return "message";
  if (node.ispf.signal) return "signal";
  return "none";
}

function EventCircle({
  w,
  h,
  variant,
  marker,
  throwing,
}: {
  w: number;
  h: number;
  variant: "start" | "intermediate" | "end";
  marker: EventMarker;
  throwing: boolean;
}) {
  const cx = w / 2;
  const cy = h / 2;
  const rOuter = Math.min(w, h) / 2 - (variant === "end" ? 2.5 : 1.5);
  const strokeW = variant === "end" ? 3.5 : 1.6;

  return (
    <g className={`bpmn-glyph bpmn-glyph-event bpmn-glyph-${variant}`}>
      <circle cx={cx} cy={cy} r={rOuter} fill={FILL} stroke={STROKE} strokeWidth={strokeW} />
      {variant === "intermediate" && (
        <circle cx={cx} cy={cy} r={rOuter - 3.5} fill="none" stroke={STROKE} strokeWidth={1.5} />
      )}
      {marker === "timer" && <TimerMarker cx={cx} cy={cy} r={rOuter * 0.55} filled={throwing} />}
      {marker === "signal" && <SignalMarker cx={cx} cy={cy} r={rOuter * 0.48} filled={throwing} />}
      {marker === "message" && (
        <EnvelopeMarker cx={cx} cy={cy} w={rOuter * 0.95} h={rOuter * 0.68} filled={throwing} />
      )}
    </g>
  );
}

function Gateway({ w, h, kind }: { w: number; h: number; kind: "exclusive" | "parallel" }) {
  const cx = w / 2;
  const cy = h / 2;
  const r = Math.min(w, h) / 2 - 2;
  const points = `${cx},${cy - r} ${cx + r},${cy} ${cx},${cy + r} ${cx - r},${cy}`;
  const m = r * 0.36;
  return (
    <g className={`bpmn-glyph bpmn-glyph-gateway bpmn-glyph-gateway-${kind}`}>
      <polygon points={points} fill={FILL} stroke={STROKE} strokeWidth={1.8} />
      {kind === "exclusive" ? (
        <g stroke={STROKE} strokeWidth={2.4} strokeLinecap="round">
          <line x1={cx - m} y1={cy - m} x2={cx + m} y2={cy + m} />
          <line x1={cx + m} y1={cy - m} x2={cx - m} y2={cy + m} />
        </g>
      ) : (
        <g stroke={STROKE} strokeWidth={2.6} strokeLinecap="round">
          <line x1={cx - m} y1={cy} x2={cx + m} y2={cy} />
          <line x1={cx} y1={cy - m} x2={cx} y2={cy + m} />
        </g>
      )}
    </g>
  );
}

function Activity({
  w,
  h,
  kind,
}: {
  w: number;
  h: number;
  kind: "abstract" | "service" | "user" | "send" | "subprocess" | "call";
}) {
  const border = kind === "subprocess" || kind === "call" ? 2.4 : 1.7;
  return (
    <g className={`bpmn-glyph bpmn-glyph-activity bpmn-glyph-${kind}`}>
      <rect
        x={1}
        y={1}
        width={w - 2}
        height={h - 2}
        rx={10}
        ry={10}
        fill={FILL}
        stroke={STROKE}
        strokeWidth={border}
      />
      {kind === "service" && <GearPair x={9} y={9} size={15} />}
      {kind === "user" && <UserMarker x={9} y={7} size={17} />}
      {kind === "send" && <EnvelopeMarker cx={18} cy={16} w={17} h={12} filled />}
      {(kind === "subprocess" || kind === "call") && <PlusBox cx={w / 2} cy={h - 13} size={13} />}
    </g>
  );
}

function TimerMarker({ cx, cy, r, filled }: { cx: number; cy: number; r: number; filled: boolean }) {
  return (
    <g fill={filled ? STROKE : "none"} stroke={STROKE} strokeWidth={1.35}>
      <circle cx={cx} cy={cy} r={r} />
      <line x1={cx} y1={cy} x2={cx} y2={cy - r * 0.58} strokeLinecap="round" />
      <line x1={cx} y1={cy} x2={cx + r * 0.42} y2={cy + r * 0.12} strokeLinecap="round" />
      {[0, 90, 180, 270].map((deg) => {
        const rad = (deg * Math.PI) / 180;
        return (
          <line
            key={deg}
            x1={cx + Math.cos(rad) * r * 0.75}
            y1={cy + Math.sin(rad) * r * 0.75}
            x2={cx + Math.cos(rad) * r * 0.95}
            y2={cy + Math.sin(rad) * r * 0.95}
            strokeWidth={1.15}
          />
        );
      })}
    </g>
  );
}

function SignalMarker({ cx, cy, r, filled }: { cx: number; cy: number; r: number; filled: boolean }) {
  const points = `${cx},${cy - r} ${cx + r * 0.92},${cy + r * 0.78} ${cx - r * 0.92},${cy + r * 0.78}`;
  return (
    <polygon
      points={points}
      fill={filled ? STROKE : "none"}
      stroke={STROKE}
      strokeWidth={1.5}
      strokeLinejoin="round"
    />
  );
}

/** Catch = outline envelope; throw/send = filled (BPMN 2.0 convention). */
function EnvelopeMarker({
  cx,
  cy,
  w,
  h,
  filled,
}: {
  cx: number;
  cy: number;
  w: number;
  h: number;
  filled: boolean;
}) {
  const x = cx - w / 2;
  const y = cy - h / 2;
  if (filled) {
    return (
      <g fill={STROKE} stroke={STROKE} strokeWidth={1.2}>
        <rect x={x} y={y} width={w} height={h} rx={1} />
        <polyline
          points={`${x},${y} ${cx},${y + h * 0.55} ${x + w},${y}`}
          fill="none"
          stroke="var(--bg-elevated)"
          strokeWidth={1.25}
        />
      </g>
    );
  }
  return (
    <g fill="none" stroke={STROKE} strokeWidth={1.35}>
      <rect x={x} y={y} width={w} height={h} rx={1} />
      <polyline points={`${x},${y} ${cx},${y + h * 0.55} ${x + w},${y}`} />
    </g>
  );
}

/** Dual gear — classic BPMN service-task marker. */
function GearPair({ x, y, size }: { x: number; y: number; size: number }) {
  return (
    <g fill="none" stroke={STROKE} strokeWidth={1.15}>
      <Gear cx={x + size * 0.38} cy={y + size * 0.38} r={size * 0.34} />
      <Gear cx={x + size * 0.72} cy={y + size * 0.68} r={size * 0.28} />
    </g>
  );
}

function Gear({ cx, cy, r }: { cx: number; cy: number; r: number }) {
  const teeth = 8;
  const inner = r * 0.55;
  const pts: string[] = [];
  for (let i = 0; i < teeth * 2; i++) {
    const ang = (Math.PI * i) / teeth - Math.PI / 2;
    const rad = i % 2 === 0 ? r : inner;
    pts.push(`${cx + Math.cos(ang) * rad},${cy + Math.sin(ang) * rad}`);
  }
  return (
    <g>
      <polygon points={pts.join(" ")} />
      <circle cx={cx} cy={cy} r={r * 0.28} />
    </g>
  );
}

function UserMarker({ x, y, size }: { x: number; y: number; size: number }) {
  const cx = x + size / 2;
  return (
    <g fill="none" stroke={STROKE} strokeWidth={1.35}>
      <circle cx={cx} cy={y + size * 0.26} r={size * 0.2} />
      <path
        d={`M ${cx - size * 0.34} ${y + size * 0.95}
            C ${cx - size * 0.34} ${y + size * 0.52}, ${cx + size * 0.34} ${y + size * 0.52}, ${cx + size * 0.34} ${y + size * 0.95}`}
      />
    </g>
  );
}

function PlusBox({ cx, cy, size }: { cx: number; cy: number; size: number }) {
  const half = size / 2;
  return (
    <g fill="none" stroke={STROKE} strokeWidth={1.4}>
      <rect x={cx - half} y={cy - half} width={size} height={size} rx={1.5} />
      <line x1={cx - half + 3} y1={cy} x2={cx + half - 3} y2={cy} />
      <line x1={cx} y1={cy - half + 3} x2={cx} y2={cy + half - 3} />
    </g>
  );
}
