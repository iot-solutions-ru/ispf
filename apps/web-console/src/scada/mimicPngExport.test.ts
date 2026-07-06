/** @vitest-environment jsdom */
import { describe, expect, it } from "vitest";
import { prepareMimicSvgForExport, resolveMimicExportBackground } from "./mimicPngExport";

describe("mimicPngExport", () => {
  it("prepareMimicSvgForExport adds xmlns and document dimensions", () => {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", "scada-mimic-svg");
    svg.setAttribute("viewBox", "0 0 100 50");
    svg.style.transform = "translate(10px, 20px) scale(1.5)";
    svg.style.background = "var(--bg)";

    const xml = prepareMimicSvgForExport(svg, 600, 280);

    expect(xml).toContain('xmlns="http://www.w3.org/2000/svg"');
    expect(xml).toContain('width="600"');
    expect(xml).toContain('height="280"');
    expect(xml).toContain('viewBox="0 0 600 280"');
    expect(xml).not.toContain("translate(10px");
    expect(xml).not.toContain("var(--bg)");
  });

  it("resolveMimicExportBackground resolves css variables", () => {
    expect(resolveMimicExportBackground("var(--bg)")).toBe("#0d1117");
    expect(resolveMimicExportBackground("#112233")).toBe("#112233");
  });
});
