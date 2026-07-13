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

  it("updates stroke without rebuilding markup string consumers", () => {
    const svg = '<path id="link_a" stroke="#178E4E"/>';
    const out = applySvgBehaviors({
      svg,
      values: { up: false },
      behaviors: [
        {
          bind: "up",
          type: "stroke",
          target: "#link_a",
          trueColor: "#178E4E",
          falseColor: "#D32F2F",
        },
      ],
    });
    expect(out).toContain('stroke="#D32F2F"');
  });
});
