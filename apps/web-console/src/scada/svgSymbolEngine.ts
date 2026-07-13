import type { MimicSymbolBehavior } from "../types/scadaMimic";
import { replaceCssVars, sanitizeSvgMarkup } from "./customSvg";
import { asBool, asNum, clamp01, fmtNumWithQuality, fmtText, isBadQuality } from "./utils";

export interface ApplySvgBehaviorsOptions {
  svg: string;
  values: Record<string, unknown>;
  behaviors?: MimicSymbolBehavior[];
  styleOverrides?: Record<string, string>;
  props?: Record<string, unknown>;
}

export interface ApplySvgBehaviorsToRootOptions {
  root: Element;
  values: Record<string, unknown>;
  behaviors?: MimicSymbolBehavior[];
  styleOverrides?: Record<string, string>;
  props?: Record<string, unknown>;
}

function resolveBool(values: Record<string, unknown>, props: Record<string, unknown>, bind: string): boolean {
  const raw = values[bind] ?? props[bind];
  return asBool(raw);
}

function resolveNum(values: Record<string, unknown>, props: Record<string, unknown>, bind: string): number | null {
  return asNum(values[bind] ?? props[bind]);
}

function setAttr(el: Element, name: string, value: string): void {
  el.setAttribute(name, value);
}

function queryTarget(doc: Document | Element, target: string): Element | null {
  if (target.startsWith("#")) {
    const id = target.slice(1);
    if (typeof CSS !== "undefined" && typeof CSS.escape === "function") {
      return doc.querySelector(`#${CSS.escape(id)}`);
    }
    return doc.querySelector(target);
  }
  return doc.querySelector(`[data-ispf-bind-target="${target}"]`);
}

/** Mutate an already-mounted SVG root (live fill/stroke without re-parsing markup). */
export function applySvgBehaviorsToRoot({
  root,
  values,
  behaviors = [],
  styleOverrides = {},
  props = {},
}: ApplySvgBehaviorsToRootOptions): void {
  for (const behavior of behaviors) {
    const el = queryTarget(root, behavior.target);
    if (!el) continue;

    switch (behavior.type) {
      case "visibility": {
        const on = resolveBool(values, props, behavior.bind);
        const show = behavior.when === "falsy" ? !on : on;
        setAttr(el, "display", show ? "" : "none");
        if (show) el.removeAttribute("display");
        break;
      }
      case "hidden": {
        const on = resolveBool(values, props, behavior.bind);
        const hide = behavior.when === "falsy" ? !on : on;
        setAttr(el, "display", hide ? "none" : "");
        if (!hide) el.removeAttribute("display");
        break;
      }
      case "text": {
        const rawVal = values[behavior.bind] ?? props[behavior.bind];
        const quality = behavior.qualityBind
          ? values[behavior.qualityBind] ?? props[behavior.qualityBind]
          : undefined;
        const gray = isBadQuality(quality) ? "#808080" : undefined;
        const text =
          behavior.format === "number"
            ? fmtNumWithQuality(
                rawVal,
                quality,
                behavior.decimals ?? 0,
                behavior.suffix ?? "",
                behavior.formatPattern
              )
            : fmtText(rawVal, "");
        el.textContent = text;
        if (gray) setAttr(el, "fill", gray);
        break;
      }
      case "fillLevel": {
        const level = resolveNum(values, props, behavior.bind);
        const max =
          resolveNum(values, props, behavior.maxBind ?? "maxLevel") ??
          asNum(props.maxLevel) ??
          100;
        const inset = behavior.inset ?? 0;
        const ratio = level != null && max > 0 ? clamp01(level / max) : 0;
        if (el.tagName.toLowerCase() === "rect") {
          const fullH = Number(el.getAttribute("data-ispf-full-height") ?? el.getAttribute("height") ?? 0);
          const fullY = Number(el.getAttribute("data-ispf-full-y") ?? el.getAttribute("y") ?? 0);
          const h = Math.max(0, (fullH - inset * 2) * ratio);
          setAttr(el, "y", String(fullY + (fullH - inset * 2 - h)));
          setAttr(el, "height", String(h));
        }
        break;
      }
      case "fill": {
        const on = resolveBool(values, props, behavior.bind);
        setAttr(el, "fill", on ? (behavior.trueColor ?? "#3fb950") : (behavior.falseColor ?? "#484f58"));
        break;
      }
      case "stroke": {
        const on = resolveBool(values, props, behavior.bind);
        setAttr(el, "stroke", on ? (behavior.trueColor ?? "#3fb950") : (behavior.falseColor ?? "#484f58"));
        break;
      }
      case "blink": {
        const on = resolveBool(values, props, behavior.bind);
        const show = behavior.when === "falsy" ? !on : on;
        if (show) {
          setAttr(el, "class", `${el.getAttribute("class") ?? ""} ispf-mimic-blink`.trim());
          setAttr(el, "data-ispf-blink", "1");
        } else {
          el.removeAttribute("data-ispf-blink");
        }
        break;
      }
    }
  }

  if (styleOverrides.stroke) {
    const accent = root.querySelector("#ispf-accent") ?? root.querySelector("[data-ispf-accent]");
    if (accent) setAttr(accent, "stroke", styleOverrides.stroke);
  }
  if (styleOverrides.fill) {
    const accent = root.querySelector("#ispf-accent") ?? root.querySelector("[data-ispf-accent]");
    if (accent) setAttr(accent, "fill", styleOverrides.fill);
  }
}

/** Apply binding values and format overrides to inner SVG markup. */
export function applySvgBehaviors({
  svg,
  values,
  behaviors = [],
  styleOverrides = {},
  props = {},
}: ApplySvgBehaviorsOptions): string {
  const raw = replaceCssVars(sanitizeSvgMarkup(svg));
  if (!raw.trim()) return raw;

  const wrapped = `<svg xmlns="http://www.w3.org/2000/svg">${raw}</svg>`;
  const doc = new DOMParser().parseFromString(wrapped, "image/svg+xml");
  const root = doc.documentElement;
  if (root.querySelector("parsererror")) return raw;

  applySvgBehaviorsToRoot({ root, values, behaviors, styleOverrides, props });
  return root.innerHTML;
}

/** Sanitize topology SVG once before mounting; live status goes through applySvgBehaviorsToRoot. */
export function prepareSvgInner(svg: string): string {
  return replaceCssVars(sanitizeSvgMarkup(svg));
}
