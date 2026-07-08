import type { MimicCustomSymbol, MimicElement, MimicRotation, ScadaMimicDocument } from "../types/scadaMimic";
import { snapCanvasCoordinate } from "./document";
import { symbolSize } from "./symbols/registry";

export const MIN_ELEMENT_SIZE = 16;

export interface ElementBounds {
  left: number;
  top: number;
  right: number;
  bottom: number;
  centerX: number;
  centerY: number;
  width: number;
  height: number;
}

export type AlignMode = "left" | "centerX" | "right" | "top" | "centerY" | "bottom";
export type DistributeAxis = "horizontal" | "vertical";
export type ResizeHandle = "n" | "s" | "e" | "w" | "nw" | "ne" | "sw" | "se";

export function getElementSize(el: MimicElement, customSymbols?: MimicCustomSymbol[]) {
  return symbolSize(el, customSymbols);
}

export function getElementBounds(el: MimicElement, customSymbols?: MimicCustomSymbol[]): ElementBounds {
  const { width, height } = symbolSize(el, customSymbols);
  return {
    left: el.x,
    top: el.y,
    right: el.x + width,
    bottom: el.y + height,
    centerX: el.x + width / 2,
    centerY: el.y + height / 2,
    width,
    height,
  };
}

export function boundsIntersect(
  a: Pick<ElementBounds, "left" | "top" | "right" | "bottom">,
  b: Pick<ElementBounds, "left" | "top" | "right" | "bottom">
): boolean {
  return !(a.right < b.left || a.left > b.right || a.bottom < b.top || a.top > b.bottom);
}

export function elementTransform(el: MimicElement, width: number, height: number): string {
  const rotation = el.rotation ?? 0;
  return `translate(${el.x},${el.y}) rotate(${rotation} ${width / 2} ${height / 2})`;
}

export function elementFlipTransform(el: MimicElement, width: number, height: number): string | undefined {
  const flipX = el.props?.flipX === true ? -1 : 1;
  const flipY = el.props?.flipY === true ? -1 : 1;
  if (flipX === 1 && flipY === 1) return undefined;
  return `translate(${width / 2},${height / 2}) scale(${flipX},${flipY}) translate(${-width / 2},${-height / 2})`;
}

export function selectionBounds(
  elements: MimicElement[],
  selectedIds: Set<string>,
  customSymbols?: MimicCustomSymbol[]
): ElementBounds | null {
  const selected = elements.filter((el) => selectedIds.has(el.id));
  if (selected.length === 0) return null;
  let left = Infinity;
  let top = Infinity;
  let right = -Infinity;
  let bottom = -Infinity;
  for (const el of selected) {
    const b = getElementBounds(el, customSymbols);
    left = Math.min(left, b.left);
    top = Math.min(top, b.top);
    right = Math.max(right, b.right);
    bottom = Math.max(bottom, b.bottom);
  }
  const width = right - left;
  const height = bottom - top;
  return {
    left,
    top,
    right,
    bottom,
    centerX: left + width / 2,
    centerY: top + height / 2,
    width,
    height,
  };
}

export function alignElements(
  elements: MimicElement[],
  selectedIds: Set<string>,
  mode: AlignMode,
  customSymbols?: MimicCustomSymbol[]
): MimicElement[] {
  if (selectedIds.size < 2) return elements;
  const bounds = selectionBounds(elements, selectedIds, customSymbols);
  if (!bounds) return elements;

  return elements.map((el) => {
    if (!selectedIds.has(el.id)) return el;
    const b = getElementBounds(el, customSymbols);
    let x = el.x;
    let y = el.y;
    switch (mode) {
      case "left":
        x = bounds.left;
        break;
      case "centerX":
        x = bounds.centerX - b.width / 2;
        break;
      case "right":
        x = bounds.right - b.width;
        break;
      case "top":
        y = bounds.top;
        break;
      case "centerY":
        y = bounds.centerY - b.height / 2;
        break;
      case "bottom":
        y = bounds.bottom - b.height;
        break;
    }
    return { ...el, x, y };
  });
}

export function distributeElements(
  elements: MimicElement[],
  selectedIds: Set<string>,
  axis: DistributeAxis,
  customSymbols?: MimicCustomSymbol[]
): MimicElement[] {
  if (selectedIds.size < 3) return elements;
  const selected = elements
    .filter((el) => selectedIds.has(el.id))
    .map((el) => ({ el, bounds: getElementBounds(el, customSymbols) }));

  if (axis === "horizontal") {
    selected.sort((a, b) => a.bounds.centerX - b.bounds.centerX);
    const first = selected[0].bounds;
    const last = selected[selected.length - 1].bounds;
    const totalSpan = last.centerX - first.centerX;
    const step = totalSpan / (selected.length - 1);
    const targetCenterX = new Map<string, number>();
    selected.forEach((item, index) => {
      targetCenterX.set(item.el.id, first.centerX + step * index);
    });
    return elements.map((el) => {
      const target = targetCenterX.get(el.id);
      if (target == null) return el;
      const b = getElementBounds(el, customSymbols);
      return { ...el, x: target - b.width / 2 };
    });
  }

  selected.sort((a, b) => a.bounds.centerY - b.bounds.centerY);
  const first = selected[0].bounds;
  const last = selected[selected.length - 1].bounds;
  const totalSpan = last.centerY - first.centerY;
  const step = totalSpan / (selected.length - 1);
  const targetCenterY = new Map<string, number>();
  selected.forEach((item, index) => {
    targetCenterY.set(item.el.id, first.centerY + step * index);
  });
  return elements.map((el) => {
    const target = targetCenterY.get(el.id);
    if (target == null) return el;
    const b = getElementBounds(el, customSymbols);
    return { ...el, y: target - b.height / 2 };
  });
}

export function flipElement(el: MimicElement, axis: "h" | "v"): MimicElement {
  const flipX = el.props?.flipX === true;
  const flipY = el.props?.flipY === true;
  const props = { ...(el.props ?? {}) };
  if (axis === "h") props.flipX = !flipX;
  else props.flipY = !flipY;
  return { ...el, props };
}

const ROTATIONS: MimicRotation[] = [0, 90, 180, 270];

export function rotateElement(el: MimicElement, delta: 90 | -90): MimicElement {
  const current = el.rotation ?? 0;
  const index = ROTATIONS.indexOf(current as MimicRotation);
  const base = index >= 0 ? index : 0;
  const next = (base + (delta === 90 ? 1 : 3)) % 4;
  return { ...el, rotation: ROTATIONS[next] };
}

export function setElementSize(
  el: MimicElement,
  width: number,
  height: number,
  _customSymbols?: MimicCustomSymbol[]
): MimicElement {
  const w = Math.max(MIN_ELEMENT_SIZE, width);
  const h = Math.max(MIN_ELEMENT_SIZE, height);
  return {
    ...el,
    scale: 1,
    props: {
      ...(el.props ?? {}),
      width: w,
      height: h,
    },
  };
}

export interface ResizeResult {
  x: number;
  y: number;
  width: number;
  height: number;
}

export function applyElementResize(
  el: MimicElement,
  handle: ResizeHandle,
  dx: number,
  dy: number,
  customSymbols?: MimicCustomSymbol[],
  options?: { aspectLock?: boolean; grid?: ScadaMimicDocument["grid"] }
): ResizeResult {
  const bounds = getElementBounds(el, customSymbols);
  let { left, top, right, bottom } = bounds;
  const aspect = bounds.width / bounds.height;

  if (handle.includes("e")) right += dx;
  if (handle.includes("w")) left += dx;
  if (handle.includes("s")) bottom += dy;
  if (handle.includes("n")) top += dy;

  if (options?.aspectLock && (handle === "nw" || handle === "ne" || handle === "sw" || handle === "se")) {
    const newW = right - left;
    const newH = bottom - top;
    if (Math.abs(dx) >= Math.abs(dy)) {
      bottom = top + newW / aspect;
    } else {
      right = left + newH * aspect;
    }
  }

  let width = right - left;
  let height = bottom - top;

  if (width < MIN_ELEMENT_SIZE) {
    if (handle.includes("w")) left = right - MIN_ELEMENT_SIZE;
    else right = left + MIN_ELEMENT_SIZE;
    width = MIN_ELEMENT_SIZE;
  }
  if (height < MIN_ELEMENT_SIZE) {
    if (handle.includes("n")) top = bottom - MIN_ELEMENT_SIZE;
    else bottom = top + MIN_ELEMENT_SIZE;
    height = MIN_ELEMENT_SIZE;
  }

  if (options?.grid) {
    left = snapCanvasCoordinate(left, options.grid);
    top = snapCanvasCoordinate(top, options.grid);
    right = snapCanvasCoordinate(right, options.grid);
    bottom = snapCanvasCoordinate(bottom, options.grid);
    width = Math.max(MIN_ELEMENT_SIZE, right - left);
    height = Math.max(MIN_ELEMENT_SIZE, bottom - top);
  }

  return { x: left, y: top, width, height };
}
