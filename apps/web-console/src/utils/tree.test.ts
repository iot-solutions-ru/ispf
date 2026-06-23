import { describe, expect, it } from "vitest";
import { buildObjectTree, siblingObjectPaths } from "./tree";
import type { ObjectSummary } from "../types";

function node(path: string, type: ObjectSummary["type"] = "CUSTOM"): ObjectSummary {
  return {
    id: path,
    path,
    type,
    displayName: path.split(".").pop() ?? path,
    description: "",
    templateId: null,
    createdAt: "",
    sortOrder: 0,
    revision: 0,
    lastChangedBy: null,
    lastChangedAt: null,
    variableNames: [],
    eventNames: [],
    federated: false,
    federationPeerId: null,
    federationRemotePath: null,
  };
}

describe("buildObjectTree", () => {
  it("shows Applications folder and app nodes but hides legacy mirror subfolders", () => {
    const objects = [
      node("root", "ROOT"),
      node("root.platform", "PLATFORM"),
      node("root.platform.applications", "APPLICATIONS"),
      node("root.platform.applications.lab-training", "APPLICATION"),
      node("root.platform.applications.lab-training.functions", "FUNCTIONS"),
      node("root.platform.dashboards", "DASHBOARDS"),
    ];

    const tree = buildObjectTree(objects);
    const root = tree.find((n) => n.object.path === "root");
    expect(root).toBeDefined();

    const platform = root!.children.find((n) => n.object.path === "root.platform");
    expect(platform).toBeDefined();

    const appsFolder = platform!.children.find((n) => n.object.path === "root.platform.applications");
    expect(appsFolder).toBeDefined();

    const labApp = appsFolder!.children.find(
      (n) => n.object.path === "root.platform.applications.lab-training",
    );
    expect(labApp).toBeDefined();
    expect(labApp!.children).toHaveLength(0);
  });

  it("lists sibling paths from flat objects for reorder", () => {
    const objects = [
      node("root.platform.dashboards", "DASHBOARDS"),
      node("root.platform.dashboards.b", "DASHBOARD"),
      node("root.platform.dashboards.a", "DASHBOARD"),
    ];
    objects[1].sortOrder = 1;
    objects[2].sortOrder = 0;

    expect(siblingObjectPaths("root.platform.dashboards", objects)).toEqual([
      "root.platform.dashboards.a",
      "root.platform.dashboards.b",
    ]);
  });

  it("builds from root children when root node is omitted (lazy API load)", () => {
    const objects = [
      node("root.platform", "PLATFORM"),
      node("root.tenant", "TENANT"),
      node("root.platform.devices", "DEVICES"),
    ];

    const tree = buildObjectTree(objects);
    expect(tree).toHaveLength(2);
    expect(tree.map((n) => n.object.path).sort()).toEqual(["root.platform", "root.tenant"]);
  });
});
