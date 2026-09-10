import { describe, expect, it } from "vitest";
import type { ObjectSummary } from "../../types";
import { filterLoadedObjectsForQuery } from "./treeSearch";

function node(path: string, displayName = path): ObjectSummary {
  return {
    id: path,
    path,
    type: "CUSTOM",
    displayName,
    description: "",
    templateId: null,
    createdAt: "",
    sortOrder: 0,
    revision: 0,
    lastChangedBy: null,
    lastChangedAt: null,
    variableNames: [],
    eventNames: [],
  };
}

describe("filterLoadedObjectsForQuery", () => {
  it("keeps matching leaves and their ancestors", () => {
    const objects = [
      node("root"),
      node("root.platform"),
      node("root.platform.devices"),
      node("root.platform.devices.pump", "Pump"),
      node("root.platform.dashboards"),
    ];
    const filtered = filterLoadedObjectsForQuery(objects, "pump");
    expect(filtered.map((item) => item.path)).toEqual([
      "root",
      "root.platform",
      "root.platform.devices",
      "root.platform.devices.pump",
    ]);
  });
});
