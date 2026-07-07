/** @vitest-environment jsdom */
import { describe, expect, it } from "vitest";
import { DEFAULT_CUSTOM_SVG_INNER, parseSvgUpload, sanitizeSvgMarkup } from "./customSvg";

describe("customSvg upload pipeline (BL-94)", () => {
  it("strips script tags and event handlers", () => {
    const dirty =
      '<rect onclick="alert(1)" width="10" height="10"/>' +
      '<script>alert("x")</script>' +
      '<rect fill="green" width="20" height="20"/>';
    const clean = sanitizeSvgMarkup(dirty);
    expect(clean).not.toContain("script");
    expect(clean).not.toContain("onclick");
    expect(clean).toContain('fill="green"');
  });

  it("parses uploaded svg root element", () => {
    const raw = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 40" width="80" height="40">
      <circle cx="40" cy="20" r="18" fill="#2f81f7"/>
    </svg>`;
    const parsed = parseSvgUpload(raw);
    expect(parsed.width).toBe(80);
    expect(parsed.height).toBe(40);
    expect(parsed.viewBox).toBe("0 0 80 40");
    expect(parsed.svg).toContain("circle");
    expect(parsed.ports).toHaveLength(4);
  });

  it("falls back for svg fragment without root", () => {
    const parsed = parseSvgUpload("<path d='M0 0 L10 10'/>");
    expect(parsed.svg.length).toBeGreaterThan(0);
    expect(parsed.width).toBe(64);
    expect(parsed.height).toBe(64);
  });

  it("uses default inner when upload empty", () => {
    const parsed = parseSvgUpload("   ");
    expect(parsed.svg).toBe(DEFAULT_CUSTOM_SVG_INNER);
  });
});
