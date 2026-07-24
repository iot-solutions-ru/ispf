import type { FlowEdge, FlowNode } from "./model/types";

export interface Point {
  x: number;
  y: number;
}

function center(n: FlowNode): Point {
  return { x: n.x + n.width / 2, y: n.y + n.height / 2 };
}

/** Intersection of ray from node center toward `toward` with the node outline. */
export function borderPoint(node: FlowNode, toward: Point): Point {
  const c = center(node);
  const dx = toward.x - c.x;
  const dy = toward.y - c.y;
  if (dx === 0 && dy === 0) return c;

  const isEvent =
    node.type === "startEvent" ||
    node.type === "endEvent" ||
    node.type === "intermediateCatchEvent" ||
    node.type === "intermediateThrowEvent" ||
    node.type === "boundaryEvent";
  const isGateway = node.type === "exclusiveGateway" || node.type === "parallelGateway";

  if (isEvent) {
    const r = Math.min(node.width, node.height) / 2;
    const len = Math.hypot(dx, dy) || 1;
    return { x: c.x + (dx / len) * r, y: c.y + (dy / len) * r };
  }

  if (isGateway) {
    // Diamond half-diagonals
    const hw = node.width / 2;
    const hh = node.height / 2;
    const len = Math.hypot(dx, dy) || 1;
    const ux = dx / len;
    const uy = dy / len;
    // Ray vs diamond: |x|/hw + |y|/hh = 1
    const denom = Math.abs(ux) / hw + Math.abs(uy) / hh;
    const t = denom > 0 ? 1 / denom : 0;
    return { x: c.x + ux * t, y: c.y + uy * t };
  }

  // Rounded rect → axis-aligned bounds
  const hw = node.width / 2;
  const hh = node.height / 2;
  const scale = 1 / Math.max(Math.abs(dx) / hw, Math.abs(dy) / hh);
  return { x: c.x + dx * scale, y: c.y + dy * scale };
}

export function edgeEndpoints(
  source: FlowNode,
  target: FlowNode
): { from: Point; to: Point; mid: Point } {
  const sc = center(source);
  const tc = center(target);
  const from = borderPoint(source, tc);
  const to = borderPoint(target, sc);
  return { from, to, mid: { x: (from.x + to.x) / 2, y: (from.y + to.y) / 2 } };
}

/** Unit direction from → to, and a perpendicular for default-flow slash. */
export function edgeDirection(from: Point, to: Point): { ux: number; uy: number; px: number; py: number } {
  const len = Math.hypot(to.x - from.x, to.y - from.y) || 1;
  const ux = (to.x - from.x) / len;
  const uy = (to.y - from.y) / len;
  return { ux, uy, px: -uy, py: ux };
}

export function isConditionalEdge(edge: FlowEdge, source: FlowNode): boolean {
  if (!edge.condition?.trim()) return false;
  // Per BPMN: diamond at source only when leaving an activity (not a gateway)
  return (
    source.type === "serviceTask" ||
    source.type === "userTask" ||
    source.type === "messageTask" ||
    source.type === "callActivity" ||
    source.type === "subProcess"
  );
}
