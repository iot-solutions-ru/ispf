import type { MouseEvent } from "react";
import {
  edgeDirection,
  edgeEndpoints,
  isConditionalEdge,
  type Point,
} from "../../bpmn/edgeGeometry";
import type { FlowEdge, FlowNode } from "../../bpmn/model/types";

interface Props {
  edge: FlowEdge;
  source: FlowNode;
  target: FlowNode;
  selected?: boolean;
  markerId: string;
  onClick?: (e: MouseEvent) => void;
}

/** Classic BPMN sequence flow: arrow, optional default slash, optional condition diamond. */
export default function BpmnSequenceFlow({
  edge,
  source,
  target,
  selected,
  markerId,
  onClick,
}: Props) {
  const { from, to, mid } = edgeEndpoints(source, target);
  const { ux, uy, px, py } = edgeDirection(from, to);
  const showDefault = Boolean(edge.isDefault);
  const showConditional = isConditionalEdge(edge, source);
  const label = edge.isDefault ? "default" : edge.condition?.trim() || edge.name;

  const slashAt: Point = {
    x: from.x + ux * 10,
    y: from.y + uy * 10,
  };
  const slashLen = 6;

  return (
    <g
      className={selected ? "ispf-bpmn-edge selected" : "ispf-bpmn-edge"}
      onClick={onClick}
    >
      <line
        x1={from.x}
        y1={from.y}
        x2={to.x}
        y2={to.y}
        stroke="currentColor"
        strokeWidth={selected ? 2.4 : 1.6}
        markerEnd={`url(#${markerId})`}
      />
      {showDefault && (
        <line
          className="bpmn-flow-default-slash"
          x1={slashAt.x + px * slashLen}
          y1={slashAt.y + py * slashLen}
          x2={slashAt.x - px * slashLen}
          y2={slashAt.y - py * slashLen}
          stroke="currentColor"
          strokeWidth={1.8}
          strokeLinecap="round"
        />
      )}
      {showConditional && (
        <polygon
          className="bpmn-flow-condition-diamond"
          points={diamondPoints(from.x + ux * 8, from.y + uy * 8, ux, uy, px, py)}
          fill="var(--bg-elevated)"
          stroke="currentColor"
          strokeWidth={1.3}
        />
      )}
      {label && (
        <text
          x={mid.x}
          y={mid.y - 8}
          textAnchor="middle"
          fontSize="11"
          className="ispf-bpmn-label"
        >
          {label.length > 28 ? `${label.slice(0, 26)}…` : label}
        </text>
      )}
    </g>
  );
}

function diamondPoints(
  cx: number,
  cy: number,
  ux: number,
  uy: number,
  px: number,
  py: number
): string {
  const along = 5;
  const across = 5;
  const a = { x: cx - ux * along, y: cy - uy * along };
  const b = { x: cx + px * across, y: cy + py * across };
  const c = { x: cx + ux * along, y: cy + uy * along };
  const d = { x: cx - px * across, y: cy - py * across };
  return `${a.x},${a.y} ${b.x},${b.y} ${c.x},${c.y} ${d.x},${d.y}`;
}
