import { describe, expect, it } from "vitest";
import { normalizeIconId, resolveTreeIconKind } from "./ObjectTreeIcon";

describe("resolveTreeIconKind", () => {
  it("uses blueprint icon for blueprint catalog folders", () => {
    expect(resolveTreeIconKind("root.platform.relative-blueprints", "BLUEPRINT")).toBe("blueprint");
    expect(resolveTreeIconKind("root.platform.absolute-blueprints", "BLUEPRINT")).toBe("blueprint");
    expect(resolveTreeIconKind("root.platform.instance-types", "BLUEPRINT")).toBe("blueprint");
  });

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

describe("normalizeIconId", () => {
  it("maps legacy model icon id to blueprint", () => {
    expect(normalizeIconId("model")).toBe("blueprint");
    expect(normalizeIconId("blueprint")).toBe("blueprint");
    expect(normalizeIconId("folder")).toBe("folder");
    expect(normalizeIconId("unknown")).toBeNull();
  });
});
