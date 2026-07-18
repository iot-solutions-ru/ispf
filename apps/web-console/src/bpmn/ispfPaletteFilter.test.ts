import { describe, expect, it } from "vitest";
import { filterIspfPaletteEntries, ISPF_PALETTE_REMOVE } from "./ispfPaletteFilter";

describe("filterIspfPaletteEntries", () => {
  it("removes unsupported palette keys and keeps supported ones", () => {
    const entries = {
      "create.start-event": {},
      "create.end-event": {},
      "create.exclusive-gateway": {},
      "create.subprocess-expanded": {},
      "create.task": {},
      "create.participant-expanded": {},
      "create.data-object": {},
      "create.data-store": {},
      "create.group": {},
      "hand-tool": {},
    };

    const filtered = filterIspfPaletteEntries(entries);

    for (const key of ISPF_PALETTE_REMOVE) {
      expect(filtered).not.toHaveProperty(key);
    }
    expect(filtered).toHaveProperty("create.start-event");
    expect(filtered).toHaveProperty("create.subprocess-expanded");
    expect(filtered).toHaveProperty("hand-tool");
  });
});
