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

  it("walks nested visual groups recursively", async () => {
    vi.mocked(api.fetchObjects).mockImplementation(async (parent) => {
      if (parent === "root.platform.data-sources") {
        return [
          {
            path: "root.platform.data-sources.outer",
            type: "VISUAL_GROUP",
            displayName: "Outer",
          } as ObjectSummary,
        ];
      }
      if (parent === "root.platform.data-sources.outer") {
        return [
          {
            path: "root.platform.data-sources.inner",
            type: "VISUAL_GROUP",
            displayName: "Inner",
          } as ObjectSummary,
          {
            path: "root.platform.data-sources.shallow",
            type: "DATA_SOURCE",
            displayName: "Shallow",
          } as ObjectSummary,
        ];
      }
      if (parent === "root.platform.data-sources.inner") {
        return [
          {
            path: "root.platform.data-sources.deep",
            type: "DATA_SOURCE",
            displayName: "Deep",
          } as ObjectSummary,
        ];
      }
      return [];
    });

    const sources = await loadDataSourceOptions();
    expect(sources.map((source) => source.path)).toEqual([
      "root.platform.data-sources.deep",
      "root.platform.data-sources.shallow",
    ]);
  });
});
