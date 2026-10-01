import { beforeEach, describe, expect, it, vi } from "vitest";
import * as api from "../../api";
import type { ObjectSummary } from "../../types";
import { loadDataSourceOptions } from "./dataSourceOptions";

vi.mock("../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api")>();
  return { ...actual, fetchObjects: vi.fn() };
});

describe("loadDataSourceOptions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("includes data sources nested under bundle visual groups", async () => {
    vi.mocked(api.fetchObjects).mockImplementation(async (parent) => {
      if (parent === "root.platform.data-sources") {
        return [
          {
            path: "root.platform.data-sources.bundle-cutoff",
            type: "VISUAL_GROUP",
            displayName: "Cutoff bundle",
          } as ObjectSummary,
          {
            path: "root.platform.data-sources.demo",
            type: "DATA_SOURCE",
            displayName: "Demo",
          } as ObjectSummary,
        ];
      }
      if (parent === "root.platform.data-sources.bundle-cutoff") {
        return [
          {
            path: "root.platform.data-sources.cutoff",
            type: "DATA_SOURCE",
            displayName: "Cutoff",
          } as ObjectSummary,
        ];
      }
      return [];
    });

    const sources = await loadDataSourceOptions();
    expect(sources.map((source) => source.path)).toEqual([
      "root.platform.data-sources.cutoff",
      "root.platform.data-sources.demo",
    ]);
  });
});
