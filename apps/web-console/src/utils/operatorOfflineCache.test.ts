/** @vitest-environment jsdom */
import { beforeEach, describe, expect, it } from "vitest";
import type { OperatorManifest } from "../types/operatorManifest";
import {
  cacheOperatorManifest,
  cachedAtForManifest,
  readCachedOperatorManifest,
  screenSupportsOfflineCache,
} from "./operatorOfflineCache";

describe("operatorOfflineCache", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("stores and reads operator manifest with cachedAt", () => {
    const manifest: OperatorManifest = {
      appId: "demo",
      title: "Demo",
      defaultScreen: "main",
      screens: [{ id: "main", title: "Main" }],
    };
    cacheOperatorManifest("demo", manifest);
    expect(readCachedOperatorManifest("demo")).toEqual(manifest);
    expect(cachedAtForManifest("demo")).toMatch(/^\d{4}-\d{2}-\d{2}T/);
  });

  it("screenSupportsOfflineCache respects offlineCache flag", () => {
    expect(
      screenSupportsOfflineCache({
        table: { objectPath: "root.a", functionName: "list" },
      })
    ).toBe(true);
    expect(
      screenSupportsOfflineCache({
        offlineCache: false,
        table: { objectPath: "root.a", functionName: "list" },
      })
    ).toBe(false);
    expect(screenSupportsOfflineCache({ id: "x", title: "Empty" } as never)).toBe(false);
  });
});
