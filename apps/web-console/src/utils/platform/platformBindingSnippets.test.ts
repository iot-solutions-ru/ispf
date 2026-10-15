import { describe, expect, it } from "vitest";
import { PLATFORM_BINDING_ENTRIES } from "./platformBindings";

/** Snippets inserted from the expression editor catalog must not use invalid placeholder forms. */
describe("platform binding catalog snippets", () => {
  it("does not advertise @/ for local-only bindings", () => {
    const localOnly = [
      "selectField",
      "scale",
      "clamp",
      "format",
      "delta",
      "rate",
      "movingAvg",
      "movingMin",
      "movingMax",
      "deadband",
      "hysteresis",
      "unitConvert",
      "sumRecordField",
    ];
    for (const id of localOnly) {
      const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === id);
      expect(entry, id).toBeDefined();
      expect(entry!.snippet, entry!.id).not.toMatch(/@\/sourceVar|@\/tableVar/);
    }
  });

  it("read template shows quoted ref with optional field", () => {
    const read = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "readRef");
    expect(read?.snippet).toContain('read("');
    expect(read?.snippet).toContain("/latitude");
  });
});
