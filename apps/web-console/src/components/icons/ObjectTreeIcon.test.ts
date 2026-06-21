import { describe, expect, it } from "vitest";
import { resolveTreeIconKind } from "./ObjectTreeIcon";
import type { ObjectType } from "../../types";

describe("resolveTreeIconKind", () => {
  it("uses database icon for data sources folder and entries", () => {
    expect(resolveTreeIconKind("root.platform.data-sources", "DATA_SOURCES")).toBe("database");
    expect(resolveTreeIconKind("root.platform.data-sources.lab-training", "DATA_SOURCE")).toBe(
      "database",
    );
  });

  it("uses screens icon for operator apps folder and application icon for children", () => {
    expect(resolveTreeIconKind("root.platform.operator-apps", "OPERATOR_APPS")).toBe("screens");
    expect(resolveTreeIconKind("root.platform.operator-apps.lab-training", "APPLICATION")).toBe(
      "application",
    );
  });
});
