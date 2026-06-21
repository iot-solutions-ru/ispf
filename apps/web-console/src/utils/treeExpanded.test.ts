import { describe, expect, it } from "vitest";
import { defaultExpandedPaths } from "./treeExpanded";
import type { TreeNode } from "../types";

function folder(path: string, children: TreeNode[] = []): TreeNode {
  return {
    object: {
      id: path,
      path,
      type: "CUSTOM",
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
    },
    children,
  };
}

describe("defaultExpandedPaths", () => {
  it("expands shallow folders by default", () => {
    const nodes = [
      folder("root", [
        folder("root.platform", [folder("root.platform.dashboards")]),
      ]),
    ];
    expect(defaultExpandedPaths(nodes)).toEqual(["root", "root.platform"]);
  });

  it("skips auto-expand for folders with many children", () => {
    const devices = Array.from({ length: 30 }, (_, i) =>
      folder(`root.platform.devices.dev-${i}`),
    );
    const nodes = [
      folder("root", [
        folder("root.platform", [folder("root.platform.devices", devices)]),
      ]),
    ];
    const paths = defaultExpandedPaths(nodes);
    expect(paths).toContain("root");
    expect(paths).toContain("root.platform");
    expect(paths).not.toContain("root.platform.devices");
  });
});
