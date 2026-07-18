import { describe, expect, it } from "vitest";
import { inferBrickClassHints } from "./brickClassHints";

describe("inferBrickClassHints", () => {
  it("maps ahu tag to Air_Handler_Unit", () => {
    const hints = inferBrickClassHints({ haystackTags: ["equip", "ahu"], haystackKind: "equip" });
    expect(hints[0]?.compactClass).toBe("brick:Air_Handler_Unit");
  });

  it("defaults to Sensor without tags", () => {
    const hints = inferBrickClassHints({});
    expect(hints[0]?.compactClass).toBe("brick:Sensor");
  });
});
