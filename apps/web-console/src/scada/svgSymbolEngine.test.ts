/** @vitest-environment jsdom */
import { describe, expect, it } from "vitest";
import { applySvgBehaviors } from "./svgSymbolEngine";

describe("applySvgBehaviors", () => {
  it("toggles visibility from boolean binding", () => {
    const svg =
      '<line id="state-closed" stroke="#3fb950"/>' +
      '<line id="state-open" stroke="#f0883e" display="none"/>';
    const out = applySvgBehaviors({
      svg,
      values: { closed: false },
      behaviors: [
        { bind: "closed", type: "visibility", target: "#state-closed", when: "truthy" },
        { bind: "closed", type: "visibility", target: "#state-open", when: "falsy" },
      ],
    });
    expect(out).toContain('id="state-closed"');
    expect(out).toContain('display="none"');
    expect(out).toContain('id="state-open"');
    expect(out).not.toMatch(/id="state-open"[^>]*display="none"/);
  });

  it("updates text binding", () => {
    const svg = '<text id="ispf-value">—</text>';
    const out = applySvgBehaviors({
      svg,
      values: { value: 12.345 },
      behaviors: [{ bind: "value", type: "text", target: "#ispf-value", format: "number", decimals: 1 }],
    });
    expect(out).toContain(">12.3<");
  });
});
