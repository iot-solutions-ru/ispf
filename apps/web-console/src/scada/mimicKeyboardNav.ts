import type { MimicCustomSymbol, MimicElement } from "../types/scadaMimic";
import { getElementBounds } from "./layoutOps";

export type MimicNavDirection = "up" | "down" | "left" | "right";

function directionScore(
  direction: MimicNavDirection,
  dx: number,
  dy: number
): number | null {
  switch (direction) {
    case "right":
      if (dx <= 0) return null;
      return dx + Math.abs(dy) * 2;
    case "left":
      if (dx >= 0) return null;
      return -dx + Math.abs(dy) * 2;
    case "down":
      if (dy <= 0) return null;
      return dy + Math.abs(dx) * 2;
    case "up":
      if (dy >= 0) return null;
      return -dy + Math.abs(dx) * 2;
  }
}

/**
 * Find the nearest element in a cardinal direction from the current selection (BL-147).
 * Uses center-to-center scoring with perpendicular-axis penalty (PowerPoint-style).
 */
export function findElementInDirection(
  elements: MimicElement[],
  fromId: string | null,
  direction: MimicNavDirection,
  customSymbols?: MimicCustomSymbol[]
): MimicElement | null {
  if (elements.length === 0) {
    return null;
  }

  const from = fromId ? elements.find((el) => el.id === fromId) ?? null : null;
  if (!from) {
    return elements[0] ?? null;
  }

  const fromBounds = getElementBounds(from, customSymbols);
  let best: MimicElement | null = null;
  let bestScore = Infinity;

  for (const candidate of elements) {
    if (candidate.id === from.id) {
      continue;
    }
    const bounds = getElementBounds(candidate, customSymbols);
    const dx = bounds.centerX - fromBounds.centerX;
    const dy = bounds.centerY - fromBounds.centerY;
    const score = directionScore(direction, dx, dy);
    if (score === null || score >= bestScore) {
      continue;
    }
    bestScore = score;
    best = candidate;
  }

  return best;
}

export function directionFromArrowKey(key: string): MimicNavDirection | null {
  switch (key) {
    case "ArrowUp":
      return "up";
    case "ArrowDown":
      return "down";
    case "ArrowLeft":
      return "left";
    case "ArrowRight":
      return "right";
    default:
      return null;
  }
}
