import { describe, expect, it } from "vitest";
import { normalizeVariablesBatch, resolveVariablesBatchEntry } from "./variablesBatchLookup";

describe("variablesBatchLookup", () => {
  it("resolves batch entries when response keys differ from requested paths", () => {
    const batch = {
      "root.platform.devices.pump-a": [{ name: "temperature" } as never],
    };
    expect(
      resolveVariablesBatchEntry(batch, "root.platform.devices.other-device"),
    ).toBeUndefined();
    expect(
      resolveVariablesBatchEntry(batch, "root.platform.devices.pump-a"),
    ).toEqual(batch["root.platform.devices.pump-a"]);
    expect(
      resolveVariablesBatchEntry(batch, "some.prefix.devices.pump-a"),
    ).toEqual(batch["root.platform.devices.pump-a"]);
  });

  it("normalizes batch maps to requested path keys", () => {
    const batch = {
      "virtual.pump": [{ name: "status" } as never],
    };
    const normalized = normalizeVariablesBatch(batch, [
      "root.platform.devices.pump",
      "virtual.pump",
    ]);
    expect(normalized["virtual.pump"]).toHaveLength(1);
    expect(normalized["root.platform.devices.pump"]).toHaveLength(1);
  });
});
