import { describe, expect, it } from "vitest";
import {
  behaviorBindingKeys,
  defaultBehavior,
  extractSvgTargetIds,
  inferBindingType,
  syncBindingSchemaFromBehaviors,
} from "./symbolBehaviors";

describe("symbolBehaviors", () => {
  it("defaultBehavior returns sensible defaults per type", () => {
    expect(defaultBehavior("text").target).toBe("#ispf-label");
    expect(defaultBehavior("fillLevel").maxBind).toBe("maxLevel");
  });

  it("inferBindingType maps behavior types", () => {
    expect(inferBindingType(defaultBehavior("fill"))).toBe("boolean");
    expect(inferBindingType(defaultBehavior("fillLevel"))).toBe("number");
    expect(inferBindingType({ bind: "v", type: "text", target: "#x", format: "number" })).toBe("number");
  });

  it("behaviorBindingKeys includes auxiliary binds", () => {
    const keys = behaviorBindingKeys([
      { bind: "fillLevel", type: "fillLevel", target: "#ispf-fill", maxBind: "maxLevel" },
      { bind: "value", type: "text", target: "#ispf-label", format: "number", qualityBind: "valueQuality" },
    ]);
    expect(keys.sort()).toEqual(["fillLevel", "maxLevel", "value", "valueQuality"]);
  });

  it("syncBindingSchemaFromBehaviors preserves existing slots", () => {
    const behaviors = [defaultBehavior("fill")];
    const schema = syncBindingSchemaFromBehaviors(behaviors, [
      { key: "running", labelKey: "bindings.running", type: "boolean" },
    ]);
    expect(schema).toHaveLength(1);
    expect(schema[0].labelKey).toBe("bindings.running");
  });

  it("extractSvgTargetIds finds ids in markup", () => {
    const ids = extractSvgTargetIds('<rect id="ispf-fill"/><text id="ispf-label"/>');
    expect(ids).toEqual(["#ispf-fill", "#ispf-label"]);
  });
});
