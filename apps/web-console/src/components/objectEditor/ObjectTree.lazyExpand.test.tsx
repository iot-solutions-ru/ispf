import { cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ObjectSummary, TreeNode } from "../../types";
import ObjectTree from "./ObjectTree";

const platformNode: ObjectSummary = {
  path: "root.platform",
  type: "PLATFORM",
  displayName: "Platform",
  description: "",
  templateId: "",
  sortOrder: 0,
};

const platformTree: TreeNode[] = [
  {
    object: platformNode,
    children: [],
  },
];

describe("ObjectTree lazy expand", () => {
  afterEach(() => {
    cleanup();
    sessionStorage.clear();
  });

  it("loads children for paths restored from session storage", async () => {
    sessionStorage.setItem("ispf-tree-expanded-paths", JSON.stringify(["root.platform"]));
    const onLoadChildren = vi.fn();

    render(
      <ObjectTree
        nodes={platformTree}
        objects={[platformNode]}
        selectedPath={null}
        selectedKeys={new Set()}
        onRowSelect={vi.fn()}
        onLoadChildren={onLoadChildren}
      />,
    );

    await waitFor(() => {
      expect(onLoadChildren).toHaveBeenCalledWith("root.platform");
    });
  });
});
