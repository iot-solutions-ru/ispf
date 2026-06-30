/**
 * Normalize converted SVG to ISPF style: CSS vars, viewBox, ports, SVGO.
 */
import fs from "node:fs";
import path from "node:path";
import { optimize } from "svgo";
import { CONVERTED_DIR, STYLIZED_DIR } from "./config.js";

export interface MimicPort {
  id: string;
  x: number;
  y: number;
}

export interface StylizedSymbol {
  svg: string;
  viewBox: string;
  width: number;
  height: number;
  ports: MimicPort[];
}

const BLOCKED_TAGS = /<\/?(script|iframe|object|embed|link|meta|style|foreignObject)\b[^>]*>/gi;
const EVENT_ATTRS = /\s(on[a-z]+|formaction|xlink:href|href)\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi;

function parseHex(hex: string): [number, number, number] | null {
  const h = hex.replace("#", "");
  if (h.length === 3) {
    return [parseInt(h[0]! + h[0], 16), parseInt(h[1]! + h[1], 16), parseInt(h[2]! + h[2], 16)];
  }
  if (h.length === 6) {
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
  }
  return null;
}

function luminance(r: number, g: number, b: number): number {
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255;
}

export function mapColorToIspf(color: string, attr: "fill" | "stroke"): string {
  const c = color.trim().toLowerCase();
  if (!c || c === "none" || c === "transparent" || c.startsWith("url(")) return color;
  if (c.startsWith("var(")) return color;

  let rgb: [number, number, number] | null = null;
  if (c.startsWith("#")) rgb = parseHex(c);
  else if (c.startsWith("rgb")) {
    const m = c.match(/[\d.]+/g);
    if (m && m.length >= 3) rgb = [Number(m[0]), Number(m[1]), Number(m[2])];
  }

  if (!rgb) {
    return attr === "stroke" ? "var(--border)" : "var(--bg-elevated)";
  }

  const [r, g, b] = rgb;
  const lum = luminance(r, g, b);

  if (b > r + 30 && b > 100) return "var(--accent)";
  if (g > r + 40 && g > b && g > 100) return "#3fb950";

  if (attr === "stroke") {
    return lum < 0.35 ? "var(--border)" : "var(--text-muted)";
  }

  if (lum > 0.55) return "var(--bg-elevated)";
  if (lum < 0.25) return "var(--border)";
  return "var(--bg-elevated)";
}

export function sanitizeSvgMarkup(raw: string): string {
  let s = raw.trim();
  if (!s) return "";
  s = s.replace(BLOCKED_TAGS, "");
  s = s.replace(EVENT_ATTRS, (match, attr: string, val: string) => {
    const lower = attr.toLowerCase();
    if (lower.startsWith("on")) return "";
    const v = val.replace(/^['"]|['"]$/g, "").trim().toLowerCase();
    if (lower === "href" || lower === "xlink:href") {
      if (v.startsWith("javascript:") || v.startsWith("data:text/html")) return "";
    }
    return match;
  });
  return s;
}

function parseViewBox(svg: string): { x: number; y: number; w: number; h: number } {
  const m = svg.match(/viewBox=["']([^"']+)["']/i);
  if (m) {
    const parts = m[1]!.trim().split(/[\s,]+/).map(Number);
    if (parts.length === 4 && parts.every((n) => Number.isFinite(n)) && parts[2]! > 0 && parts[3]! > 0) {
      return { x: parts[0]!, y: parts[1]!, w: parts[2]!, h: parts[3]! };
    }
  }
  const wm = svg.match(/\bwidth=["']([\d.]+)/i);
  const hm = svg.match(/\bheight=["']([\d.]+)/i);
  const w = wm ? Number(wm[1]) : 64;
  const h = hm ? Number(hm[1]) : 64;
  return { x: 0, y: 0, w: w > 0 ? w : 64, h: h > 0 ? h : 64 };
}

function replaceColorsInMarkup(inner: string): string {
  let s = inner;
  s = s.replace(/<text\b[^>]*>[\s\S]*?<\/text>/gi, "");
  s = s.replace(/\bfill=["']([^"']+)["']/gi, (_, col) => `fill="${mapColorToIspf(col, "fill")}"`);
  s = s.replace(/\bstroke=["']([^"']+)["']/gi, (_, col) => `stroke="${mapColorToIspf(col, "stroke")}"`);
  s = s.replace(/stroke-width=["']([\d.]+)["']/gi, () => `stroke-width="1.5"`);
  return s;
}

export function defaultEdgePorts(width: number, height: number): MimicPort[] {
  return [
    { id: "n", x: Math.round(width / 2), y: 0 },
    { id: "s", x: Math.round(width / 2), y: Math.round(height) },
    { id: "e", x: Math.round(width), y: Math.round(height / 2) },
    { id: "w", x: 0, y: Math.round(height / 2) },
  ];
}

function targetSize(w: number, h: number): { width: number; height: number } {
  const maxDim = Math.max(w, h);
  const scale = maxDim > 0 ? 64 / maxDim : 1;
  return {
    width: Math.max(32, Math.min(96, Math.round(w * scale))),
    height: Math.max(32, Math.min(96, Math.round(h * scale))),
  };
}

function stripInkscapeMetadata(inner: string): string {
  return inner
    .replace(/<sodipodi:[^>]*\/>/gi, "")
    .replace(/<sodipodi:[^>]*>[\s\S]*?<\/sodipodi:[^>]*>/gi, "")
    .replace(/<inkscape:[^>]*\/>/gi, "")
    .replace(/<inkscape:[^>]*>[\s\S]*?<\/inkscape:[^>]*>/gi, "")
    .replace(/\s(?:inkscape|sodipodi):[a-z-]+="[^"]*"/gi, "");
}

export function stylizeSvgRaw(rawSvg: string): StylizedSymbol | null {
  const vb = parseViewBox(rawSvg);
  const innerMatch = rawSvg.match(/<svg[^>]*>([\s\S]*)<\/svg>/i);
  let inner = innerMatch ? innerMatch[1]! : rawSvg;
  inner = stripInkscapeMetadata(inner);
  inner = replaceColorsInMarkup(inner);
  inner = sanitizeSvgMarkup(inner);
  if (!inner.trim()) return null;

  let outSvg: string;
  try {
    const optimized = optimize(
      `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb.x} ${vb.y} ${vb.w} ${vb.h}">${inner}</svg>`,
      {
        multipass: true,
        plugins: [
          "preset-default",
          { name: "removeViewBox", active: false },
          { name: "removeDimensions", active: true },
        ],
      }
    );
    outSvg = optimized.data;
  } catch {
    outSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb.x} ${vb.y} ${vb.w} ${vb.h}">${inner}</svg>`;
  }
  const outVb = parseViewBox(outSvg);
  const { width, height } = targetSize(outVb.w, outVb.h);
  const viewBox = `0 0 ${outVb.w} ${outVb.h}`;
  const innerOut = outSvg.replace(/<svg[^>]*>/i, "").replace(/<\/svg>\s*$/i, "");
  const svg = sanitizeSvgMarkup(innerOut);
  if (!svg.trim()) return null;

  return { svg, viewBox, width, height, ports: defaultEdgePorts(width, height) };
}

export function stylizeFile(inputPath: string): StylizedSymbol | null {
  return stylizeSvgRaw(fs.readFileSync(inputPath, "utf8"));
}

export function stylizeAll(): number {
  fs.mkdirSync(STYLIZED_DIR, { recursive: true });
  if (!fs.existsSync(CONVERTED_DIR)) {
    throw new Error(`Missing ${CONVERTED_DIR} — run convert-wmf first`);
  }
  const files = fs.readdirSync(CONVERTED_DIR).filter((f) => f.endsWith(".svg"));
  let ok = 0;
  for (const file of files) {
    const result = stylizeFile(path.join(CONVERTED_DIR, file));
    if (!result) continue;
    const slug = file.replace(/\.svg$/i, "");
    fs.writeFileSync(path.join(STYLIZED_DIR, `${slug}.json`), JSON.stringify(result));
    ok++;
  }
  console.log(`Stylized ${ok}/${files.length} symbols`);
  return ok;
}

if (process.argv[1]?.replace(/\\/g, "/").endsWith("stylize-svg.ts")) {
  stylizeAll();
}
