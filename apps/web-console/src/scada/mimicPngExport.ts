import { replaceCssVars } from "./customSvg";

export interface MimicPngExportOptions {
  width: number;
  height: number;
  filename: string;
  backgroundColor: string;
}

/** Prepare a standalone SVG string suitable for rasterization (full document, no pan/zoom). */
export function prepareMimicSvgForExport(svg: SVGSVGElement, width: number, height: number): string {
  const exportWidth = Math.max(1, Math.round(width));
  const exportHeight = Math.max(1, Math.round(height));
  const clone = svg.cloneNode(true) as SVGSVGElement;
  clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
  clone.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
  clone.setAttribute("width", String(exportWidth));
  clone.setAttribute("height", String(exportHeight));
  clone.setAttribute("viewBox", `0 0 ${exportWidth} ${exportHeight}`);
  clone.setAttribute("preserveAspectRatio", "xMidYMid meet");
  clone.style.transform = "";
  clone.style.transformOrigin = "";
  if (clone.style.background) {
    clone.style.background = replaceCssVars(clone.style.background);
  }
  return replaceCssVars(new XMLSerializer().serializeToString(clone));
}

export function resolveMimicExportBackground(background: string | undefined): string {
  const resolved = replaceCssVars(background?.trim() || "var(--bg)");
  if (/^#|^rgb/i.test(resolved)) {
    return resolved;
  }
  if (typeof document !== "undefined") {
    const fromRoot = getComputedStyle(document.documentElement).getPropertyValue("--bg").trim();
    if (fromRoot) {
      return fromRoot;
    }
  }
  return "#0d1117";
}

function loadSvgImage(svgMarkup: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const blob = new Blob([svgMarkup], { type: "image/svg+xml;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("SVG rasterization failed"));
    };
    image.src = url;
  });
}

function canvasToPngBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((png) => {
      if (!png) {
        reject(new Error("PNG encoding failed"));
        return;
      }
      resolve(png);
    }, "image/png");
  });
}

function triggerDownload(blob: Blob, filename: string): void {
  const link = document.createElement("a");
  link.download = filename;
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
}

export async function exportMimicSvgToPng(
  svg: SVGSVGElement,
  options: MimicPngExportOptions
): Promise<void> {
  const svgMarkup = prepareMimicSvgForExport(svg, options.width, options.height);
  const image = await loadSvgImage(svgMarkup);
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(options.width));
  canvas.height = Math.max(1, Math.round(options.height));
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    throw new Error("Canvas not available");
  }
  ctx.fillStyle = options.backgroundColor;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
  const png = await canvasToPngBlob(canvas);
  triggerDownload(png, options.filename);
}
