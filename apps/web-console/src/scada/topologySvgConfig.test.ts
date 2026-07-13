import { describe, expect, it } from "vitest";
import {
  collectTopologyBindingPaths,
  extractSvgInnerFromDocument,
  parseTopologyConfig,
} from "./topologySvgConfig";

describe("topologySvgConfig", () => {
  it("parseTopologyConfig reads bindings and behaviors", () => {
    const config = parseTopologyConfig(
      JSON.stringify({
        bindings: { TP14: { objectPath: "root.dev.cpu5", variableName: "status", valueField: "online" } },
        behaviors: [{ bind: "TP14", type: "fill", target: "#back_TP14" }],
        hitAreas: [{ nodeName: "TP14", objectPath: "root.dev.tp14" }],
        svgInner: "<rect id='back_TP14' />",
      })
    );
    expect(config?.bindings.TP14.objectPath).toBe("root.dev.cpu5");
    expect(config?.behaviors).toHaveLength(1);
    expect(config?.hitAreas[0].nodeName).toBe("TP14");
  });

  it("collectTopologyBindingPaths deduplicates object paths", () => {
    const paths = collectTopologyBindingPaths({
      a: { objectPath: "root.dev.a", variableName: "status", valueField: "online" },
      b: { objectPath: "root.dev.a", variableName: "linkStatus", valueField: "value" },
    });
    expect(paths).toEqual(["root.dev.a"]);
  });

  it("extractSvgInnerFromDocument strips outer svg tag", () => {
    expect(extractSvgInnerFromDocument('<svg viewBox="0 0 10 10"><circle /></svg>')).toBe("<circle />");
  });
});
