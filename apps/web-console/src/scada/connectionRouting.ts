import type { MimicConnection, MimicCustomSymbol, MimicElement, MimicPort } from "../types/scadaMimic";
import { resolveElementSymbol, symbolSize } from "./symbols/registry";

export interface PortPosition {
  elementId: string;
  portId: string;
  x: number;
  y: number;
}

export function getElementPortPosition(
  element: MimicElement,
  portId: string,
  customSymbols?: MimicCustomSymbol[]
): PortPosition | null {
  const symbol = resolveElementSymbol(element, customSymbols);
  if (!symbol) return null;
  const port = symbol.ports.find((p) => p.id === portId);
  if (!port) return null;
  const { width, height } = symbolSize(element, customSymbols);
  const scaleX = width / symbol.defaultWidth;
  const scaleY = height / symbol.defaultHeight;
  const cx = element.x + width / 2;
  const cy = element.y + height / 2;
  const px = element.x + port.x * scaleX;
  const py = element.y + port.y * scaleY;
  if (element.rotation === 90) {
    return { elementId: element.id, portId, x: cx + (py - cy), y: cy - (px - cx) };
  }
  if (element.rotation === 180) {
    return {
      elementId: element.id,
      portId,
      x: element.x + width - (px - element.x),
      y: element.y + height - (py - element.y),
    };
  }
  if (element.rotation === 270) {
    return { elementId: element.id, portId, x: cx - (py - cy), y: cy + (px - cx) };
  }
  return { elementId: element.id, portId, x: px, y: py };
}

export function connectionPolylinePoints(
  connection: MimicConnection,
  elements: MimicElement[],
  customSymbols?: MimicCustomSymbol[]
): { x: number; y: number }[] {
  return recomputeConnectionPoints(connection, elements, customSymbols);
}

/** Recompute the full orthogonal path from current port positions. */
export function recomputeConnectionPoints(
  connection: MimicConnection,
  elements: MimicElement[],
  customSymbols?: MimicCustomSymbol[]
): { x: number; y: number }[] {
  const byId = new Map(elements.map((el) => [el.id, el]));
  const fromEl = byId.get(connection.from.elementId);
  const toEl = byId.get(connection.to.elementId);
  if (!fromEl || !toEl) return connection.points;
  const from = getElementPortPosition(fromEl, connection.from.port, customSymbols);
  const to = getElementPortPosition(toEl, connection.to.port, customSymbols);
  if (!from || !to) return connection.points;

  return routeOrthogonal(from.x, from.y, to.x, to.y);
}

export function routeOrthogonal(
  x1: number,
  y1: number,
  x2: number,
  y2: number
): { x: number; y: number }[] {
  const midX = (x1 + x2) / 2;
  return [
    { x: x1, y: y1 },
    { x: midX, y: y1 },
    { x: midX, y: y2 },
    { x: x2, y: y2 },
  ];
}

export function snapPortNear(
  x: number,
  y: number,
  elements: MimicElement[],
  threshold = 24,
  customSymbols?: MimicCustomSymbol[]
): PortPosition | null {
  let best: PortPosition | null = null;
  let bestDist = threshold;
  for (const el of elements) {
    const symbol = resolveElementSymbol(el, customSymbols);
    if (!symbol) continue;
    for (const port of symbol.ports) {
      const pos = getElementPortPosition(el, port.id, customSymbols);
      if (!pos) continue;
      const dist = Math.hypot(pos.x - x, pos.y - y);
      if (dist < bestDist) {
        bestDist = dist;
        best = pos;
      }
    }
  }
  return best;
}

export function findNearestPort(
  elements: MimicElement[],
  x: number,
  y: number,
  excludeElementId?: string,
  threshold = 24,
  customSymbols?: MimicCustomSymbol[]
): { element: MimicElement; port: MimicPort; x: number; y: number } | null {
  const hit = snapPortNear(
    x,
    y,
    elements.filter((el) => el.id !== excludeElementId),
    threshold,
    customSymbols
  );
  if (!hit) return null;
  const element = elements.find((el) => el.id === hit.elementId);
  if (!element) return null;
  const symbol = resolveElementSymbol(element, customSymbols);
  const port = symbol?.ports.find((p) => p.id === hit.portId);
  if (!port) return null;
  return { element, port, x: hit.x, y: hit.y };
}

/** Nearest port on a single element (for connect-by-element-click). */
export function findPortOnElement(
  element: MimicElement,
  x: number,
  y: number,
  customSymbols?: MimicCustomSymbol[]
): { port: MimicPort; x: number; y: number } | null {
  const symbol = resolveElementSymbol(element, customSymbols);
  if (!symbol || symbol.ports.length === 0) return null;
  let best: { port: MimicPort; x: number; y: number } | null = null;
  let bestDist = Infinity;
  for (const port of symbol.ports) {
    const pos = getElementPortPosition(element, port.id, customSymbols);
    if (!pos) continue;
    const dist = Math.hypot(pos.x - x, pos.y - y);
    if (dist < bestDist) {
      bestDist = dist;
      best = { port, x: pos.x, y: pos.y };
    }
  }
  return best;
}

export function rerouteConnectionsForElement(
  elementId: string,
  connections: MimicConnection[],
  elements: MimicElement[],
  customSymbols?: MimicCustomSymbol[]
): MimicConnection[] {
  return connections.map((conn) => {
    if (conn.from.elementId !== elementId && conn.to.elementId !== elementId) {
      return conn;
    }
    const points = recomputeConnectionPoints(conn, elements, customSymbols);
    return { ...conn, points };
  });
}
