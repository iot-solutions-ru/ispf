import type { MimicCustomSymbol, MimicElement, ScadaMimicDocument } from "../types/scadaMimic";
import { snapCanvasCoordinate } from "./document";
import { getElementBounds } from "./layoutOps";
import { getElementPortPosition } from "./connectionRouting";
import { resolveElementSymbol } from "./symbols/registry";

export const SNAP_THRESHOLD = 10;

export interface SnapGuide {
  orientation: "h" | "v";
  position: number;
}

export interface SnapResult {
  x: number;
  y: number;
  guides: SnapGuide[];
}

interface SnapTarget {
  x: number;
  y: number;
}

function collectSnapTargets(
  elements: MimicElement[],
  excludeIds: Set<string>,
  customSymbols?: MimicCustomSymbol[]
): { xs: number[]; ys: number[] } {
  const xs: number[] = [];
  const ys: number[] = [];
  for (const el of elements) {
    if (excludeIds.has(el.id)) continue;
    const b = getElementBounds(el, customSymbols);
    xs.push(b.left, b.centerX, b.right);
    ys.push(b.top, b.centerY, b.bottom);
    const symbol = resolveElementSymbol(el, customSymbols);
    if (symbol) {
      for (const port of symbol.ports) {
        const pos = getElementPortPosition(el, port.id, customSymbols);
        if (pos) {
          xs.push(pos.x);
          ys.push(pos.y);
        }
      }
    }
  }
  return { xs, ys };
}

function collectMovingAnchors(
  originX: number,
  originY: number,
  el: MimicElement,
  x: number,
  y: number,
  customSymbols?: MimicCustomSymbol[]
): SnapTarget[] {
  const dx = x - originX;
  const dy = y - originY;
  const b = getElementBounds({ ...el, x: originX, y: originY }, customSymbols);
  const anchors: SnapTarget[] = [
    { x: b.left + dx, y: b.top + dy },
    { x: b.centerX + dx, y: b.top + dy },
    { x: b.right + dx, y: b.top + dy },
    { x: b.left + dx, y: b.centerY + dy },
    { x: b.centerX + dx, y: b.centerY + dy },
    { x: b.right + dx, y: b.centerY + dy },
    { x: b.left + dx, y: b.bottom + dy },
    { x: b.centerX + dx, y: b.bottom + dy },
    { x: b.right + dx, y: b.bottom + dy },
  ];
  const symbol = resolveElementSymbol(el, customSymbols);
  if (symbol) {
    for (const port of symbol.ports) {
      const pos = getElementPortPosition({ ...el, x: originX, y: originY }, port.id, customSymbols);
      if (pos) anchors.push({ x: pos.x + dx, y: pos.y + dy });
    }
  }
  return anchors;
}

function nearestSnap(
  anchors: number[],
  targets: number[],
  threshold: number
): { delta: number; guide: number } | null {
  let best: { delta: number; guide: number } | null = null;
  let bestDist = threshold;
  for (const anchor of anchors) {
    for (const target of targets) {
      const dist = Math.abs(anchor - target);
      if (dist < bestDist) {
        bestDist = dist;
        best = { delta: target - anchor, guide: target };
      }
    }
  }
  return best;
}

export function snapElementPosition(
  el: MimicElement,
  originX: number,
  originY: number,
  x: number,
  y: number,
  allElements: MimicElement[],
  excludeIds: Set<string>,
  grid: ScadaMimicDocument["grid"] | undefined,
  customSymbols?: MimicCustomSymbol[]
): SnapResult {
  let sx = snapCanvasCoordinate(x, grid);
  let sy = snapCanvasCoordinate(y, grid);
  const guides: SnapGuide[] = [];

  const { xs, ys } = collectSnapTargets(allElements, excludeIds, customSymbols);
  if (xs.length === 0 && ys.length === 0) {
    return { x: sx, y: sy, guides };
  }

  const moving = collectMovingAnchors(originX, originY, el, sx, sy, customSymbols);
  const snapX = nearestSnap(
    moving.map((a) => a.x),
    xs,
    SNAP_THRESHOLD
  );
  const snapY = nearestSnap(
    moving.map((a) => a.y),
    ys,
    SNAP_THRESHOLD
  );

  if (snapX) {
    sx += snapX.delta;
    guides.push({ orientation: "v", position: snapX.guide });
  }
  if (snapY) {
    sy += snapY.delta;
    guides.push({ orientation: "h", position: snapY.guide });
  }

  return { x: sx, y: sy, guides };
}
