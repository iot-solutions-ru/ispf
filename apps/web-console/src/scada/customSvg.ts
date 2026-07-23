import DOMPurify from "dompurify";
import type { MimicCustomSymbol, MimicPort } from "../types/scadaMimic";

export const DEFAULT_CUSTOM_SVG_INNER =
  '<rect x="8" y="8" width="48" height="48" rx="6" fill="#161b22" stroke="#30363d" stroke-width="2"/>' +
  '<text x="32" y="38" text-anchor="middle" fill="#8b949e" font-size="11">SVG</text>';

const CSS_VAR_FALLBACKS: Record<string, string> = {
  "--bg": "#0d1117",
  "--bg-elevated": "#161b22",
  "--text": "#e6edf3",
  "--text-muted": "#8b949e",
  "--border": "#30363d",
  "--accent": "#2f81f7",
};

export function replaceCssVars(raw: string): string {
  return raw.replace(/var\((--[a-z0-9-]+)\)/gi, (_, name: string) => CSS_VAR_FALLBACKS[name.toLowerCase()] ?? "#8b949e");
}

export function buildCustomSvgPlaceholder(width: number, height: number, label: string): string {
  const w = Math.max(16, Math.round(width));
  const h = Math.max(16, Math.round(height));
  const pad = Math.min(8, Math.floor(Math.min(w, h) * 0.08));
  const safe = label.replace(/[<>&"]/g, "");
  return (
    `<rect x="${pad}" y="${pad}" width="${w - pad * 2}" height="${h - pad * 2}" rx="4" ` +
    `fill="#161b22" stroke="#30363d" stroke-width="1.5" stroke-dasharray="4 2"/>` +
    `<text x="${w / 2}" y="${h / 2 + 4}" text-anchor="middle" fill="#8b949e" font-size="${Math.max(9, Math.min(12, h / 6))}">${safe || "SVG"}</text>`
  );
}

export function parseViewBoxString(viewBox: string | undefined, fallbackW: number, fallbackH: number): { w: number; h: number; viewBox: string } {
  if (!viewBox) return { w: fallbackW, h: fallbackH, viewBox: `0 0 ${fallbackW} ${fallbackH}` };
  const parts = viewBox.trim().split(/[\s,]+/).map(Number);
  if (parts.length !== 4 || parts.some((n) => !Number.isFinite(n)) || parts[2] <= 0 || parts[3] <= 0) {
    return { w: fallbackW, h: fallbackH, viewBox: `0 0 ${fallbackW} ${fallbackH}` };
  }
  return { w: parts[2], h: parts[3], viewBox: parts.join(" ") };
}

export function sanitizeSvgMarkup(raw: string): string {
  const s = raw.trim();
  if (!s) return "";
  // DOMPurify honors the SVG profile only inside an <svg> root (HTML parser namespaces),
  // so wrap the fragment and unwrap it back after sanitizing.
  const wrapped = DOMPurify.sanitize(`<svg>${s}</svg>`, {
    USE_PROFILES: { svg: true, svgFilters: true },
    // <use> with local refs is common in uploaded symbol packs and is not in the default profile.
    ADD_TAGS: ["use"],
    FORBID_TAGS: ["style"],
  });
  const match = wrapped.match(/^<svg[^>]*>([\s\S]*)<\/svg>$/);
  return match ? match[1] : "";
}

function parseViewBox(viewBox: string | null | undefined): { w: number; h: number; viewBox: string } | null {
  if (!viewBox) return null;
  const parts = viewBox.trim().split(/[\s,]+/).map(Number);
  if (parts.length !== 4 || parts.some((n) => !Number.isFinite(n))) return null;
  const w = parts[2];
  const h = parts[3];
  if (w <= 0 || h <= 0) return null;
  return { w, h, viewBox: parts.join(" ") };
}

/** Parse uploaded SVG file or fragment into storable custom symbol fields. */
export function parseSvgUpload(raw: string): Pick<MimicCustomSymbol, "svg" | "width" | "height" | "viewBox" | "ports"> {
  const trimmed = raw.trim();
  const doc = new DOMParser().parseFromString(trimmed, "image/svg+xml");
  const svgEl = doc.querySelector("svg");
  if (!svgEl) {
    const inner = sanitizeSvgMarkup(trimmed);
    const w = 64;
    const h = 64;
    return { svg: inner || DEFAULT_CUSTOM_SVG_INNER, width: w, height: h, viewBox: `0 0 ${w} ${h}`, ports: defaultEdgePorts(w, h) };
  }

  const vb = parseViewBox(svgEl.getAttribute("viewBox"));
  let width = Number(svgEl.getAttribute("width")?.replace(/px$/i, ""));
  let height = Number(svgEl.getAttribute("height")?.replace(/px$/i, ""));
  if (!Number.isFinite(width) || width <= 0) width = vb?.w ?? 64;
  if (!Number.isFinite(height) || height <= 0) height = vb?.h ?? 64;
  const viewBox = vb?.viewBox ?? `0 0 ${width} ${height}`;

  const inner = sanitizeSvgMarkup(svgEl.innerHTML);
  return {
    svg: inner || DEFAULT_CUSTOM_SVG_INNER,
    width: Math.round(width),
    height: Math.round(height),
    viewBox,
    ports: defaultEdgePorts(Math.round(width), Math.round(height)),
  };
}

export function defaultEdgePorts(width: number, height: number): MimicPort[] {
  return [
    { id: "n", x: width / 2, y: 0 },
    { id: "s", x: width / 2, y: height },
    { id: "e", x: width, y: height / 2 },
    { id: "w", x: 0, y: height / 2 },
  ];
}

export function portsFromProps(props: Record<string, unknown> | undefined, width: number, height: number): MimicPort[] {
  const raw = props?.ports;
  if (Array.isArray(raw) && raw.length > 0) {
    return raw
      .filter((p): p is MimicPort => p && typeof p === "object" && typeof (p as MimicPort).id === "string")
      .map((p) => ({
        id: p.id,
        x: Number(p.x) || 0,
        y: Number(p.y) || 0,
        direction: p.direction,
      }));
  }
  return defaultEdgePorts(width, height);
}
