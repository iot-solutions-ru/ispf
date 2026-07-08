import { describe, expect, it } from "vitest";
import { loadPackManifest, PACK_CATEGORY_IDS } from "./symbolPackLoader";

describe("symbolPackLoader", () => {
  it("loads pack manifest", async () => {
    const manifest = await loadPackManifest();
    expect(manifest).not.toBeNull();
    expect(manifest!.id).toBe("ispf-pid-v1");
    expect(manifest!.totalSymbols).toBeGreaterThanOrEqual(200);
    expect(manifest!.categories.length).toBe(PACK_CATEGORY_IDS.length);
  });
});
