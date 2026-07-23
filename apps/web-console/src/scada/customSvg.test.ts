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

  it("blocks svg/onload and mangled script vectors", () => {
    const onload = sanitizeSvgMarkup("<svg/onload=alert(1)>");
    expect(onload).not.toMatch(/onload|alert/i);
    const mangled = sanitizeSvgMarkup("<scri<script>pt>alert(1)</scri</script>pt>");
    expect(mangled.toLowerCase()).not.toContain("<script");
  });

  it("strips javascript: and data:text/html hrefs", () => {
    const entity = sanitizeSvgMarkup('<a href="&#106;avascript:alert(1)"><rect width="5"/></a>');
    expect(entity).toContain("<rect");
    expect(entity).not.toContain("href");
    const xlink = sanitizeSvgMarkup('<a xlink:href="javascript:alert(1)"><circle r="2"/></a>');
    expect(xlink).toContain("<circle");
    expect(xlink).not.toContain("xlink:href");
    const dataHtml = sanitizeSvgMarkup('<a href="data:text/html,<b>1</b>"><circle r="3"/></a>');
    expect(dataHtml).not.toContain("data:text/html");
    const useJs = sanitizeSvgMarkup('<use href="javascript:alert(1)"/>');
    expect(useJs).not.toContain("javascript");
  });

  it("removes foreignObject together with nested HTML", () => {
    const clean = sanitizeSvgMarkup(
      '<foreignObject><body xmlns="http://www.w3.org/1999/xhtml"><img src=x onerror=alert(1)></body></foreignObject>' +
        '<rect fill="green"/>'
    );
    expect(clean).not.toContain("foreignObject");
    expect(clean).not.toContain("<img");
    expect(clean).toContain('fill="green"');
  });

  it("keeps safe references: use, external image, url(#) fill", () => {
    const clean = sanitizeSvgMarkup(
      '<defs><path id="p" d="M0 0h10"/></defs>' +
        '<use href="#p"/><use xlink:href="#p"/>' +
        '<image href="https://example.com/x.png"/>' +
        '<rect fill="url(#grad)"/>'
    );
    expect(clean).toContain("<use");
    expect(clean).toContain('href="#p"');
    expect(clean).toContain('href="https://example.com/x.png"');
    expect(clean).toContain('fill="url(#grad)"');
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
