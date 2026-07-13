import { describe, expect, it } from "vitest";
import { resolveSvgInteractiveConfig, serializeSvgInteractivePatch } from "./svgWidgetInteractive";
import type { SvgWidget } from "../types/dashboard";

describe("svgWidgetInteractive", () => {
  it("reads scada-style fields like custom symbol", () => {
    const widget: SvgWidget = {
      id: "t",
      type: "svg-widget",
      title: "T",
      x: 0,
      y: 0,
      w: 1,
      h: 1,
      behaviorsJson: JSON.stringify([{ bind: "TP14", type: "fill", target: "#back_TP14" }]),
      bindingsJson: JSON.stringify({
        TP14: { objectPath: "root.dev.tp14", variableName: "status", valueField: "online", transform: "bool" },
      }),
      svgInnerJson: "<rect id='back_TP14' />",
    };
    const config = resolveSvgInteractiveConfig(widget);
    expect(config?.behaviors).toHaveLength(1);
    expect(config?.bindings.TP14.objectPath).toBe("root.dev.tp14");
    expect(config?.bindingSchema[0].key).toBe("TP14");
  });

  it("falls back to legacy topologyJson", () => {
    const widget: SvgWidget = {
      id: "t",
      type: "svg-widget",
      title: "T",
      x: 0,
      y: 0,
      w: 1,
      h: 1,
      topologyJson: JSON.stringify({
        bindings: { L1: { objectPath: "root.dev.a", variableName: "status", valueField: "online" } },
        behaviors: [{ bind: "L1", type: "stroke", target: "#link_1" }],
        hitAreas: [],
        svgInner: "<path id='link_1' />",
      }),
    };
    const config = resolveSvgInteractiveConfig(widget);
    expect(config?.behaviors[0].type).toBe("stroke");
  });

  it("serializeSvgInteractivePatch clears topologyJson", () => {
    const patch = serializeSvgInteractivePatch({
      behaviors: [],
      bindings: {},
      bindingSchema: [],
      hitAreas: [],
    });
    expect(patch.topologyJson).toBeUndefined();
    expect(patch.behaviorsJson).toBe("[]");
  });
});
