import { describe, expect, it } from "vitest";
import {
  defaultEdgePorts,
  mapColorToIspf,
  sanitizeSvgMarkup,
  stylizeSvgRaw,
} from "./stylize-svg.js";

describe("stylize-svg", () => {
  it("maps light fills to ISPF elevated background", () => {
    expect(mapColorToIspf("#ffffff", "fill")).toBe("var(--bg-elevated)");
    expect(mapColorToIspf("#c0c0c0", "fill")).toBe("var(--bg-elevated)");
  });

  it("maps dark strokes to border", () => {
    expect(mapColorToIspf("#000000", "stroke")).toBe("var(--border)");
  });

  it("strips script tags in sanitizeSvgMarkup", () => {
    const raw = '<rect fill="#fff"/><script>alert(1)</script>';
    expect(sanitizeSvgMarkup(raw)).not.toContain("script");
  });

  it("produces ports on all four edges", () => {
    const ports = defaultEdgePorts(64, 48);
    expect(ports.map((p) => p.id).sort()).toEqual(["e", "n", "s", "w"]);
    expect(ports.find((p) => p.id === "n")?.y).toBe(0);
  });

  it("stylizes minimal SVG with CSS variables", () => {
    const raw = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
      <rect x="10" y="10" width="80" height="80" fill="#ffffff" stroke="#000000" stroke-width="4"/>
    </svg>`;
    const result = stylizeSvgRaw(raw);
    expect(result).not.toBeNull();
    expect(result!.svg).toContain("var(--bg-elevated)");
    expect(result!.svg).toContain("var(--border)");
    expect(result!.ports.length).toBe(4);
    expect(result!.width).toBeGreaterThan(0);
  });

  it("strips inkscape metadata before optimize", () => {
    const raw = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 50 50">
      <sodipodi:namedview id="nv" pagecolor="#ffffff"/>
      <path fill="#fff" stroke="#000" d="M0 0 L50 50"/>
    </svg>`;
    const result = stylizeSvgRaw(raw);
    expect(result).not.toBeNull();
    expect(result!.svg).not.toContain("sodipodi");
  });
});
