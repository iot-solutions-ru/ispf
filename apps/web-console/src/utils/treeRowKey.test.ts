import { describe, expect, it } from "vitest";
import type { ObjectSummary } from "../types";
import { buildObjectTree } from "./tree";
import { objectTreeKey, parseTreeRowKey, treeRowKey } from "./treeRowKey";

describe("treeRowKey", () => {
  it("builds composite keys for group refs", () => {
    const ref: ObjectSummary = {
      id: "1",
      path: "root.platform.devices.a",
      type: "DEVICE",
      displayName: "A",
      description: "",
      templateId: null,
      createdAt: "",
      sortOrder: 0,
      variableNames: [],
      eventNames: [],
      groupRef: true,
      groupContextPath: "root.platform.devices.group",
    };
    expect(objectTreeKey(ref)).toBe("root.platform.devices.group::root.platform.devices.a");
    expect(parseTreeRowKey(objectTreeKey(ref))).toEqual({
      viaGroup: "root.platform.devices.group",
      path: "root.platform.devices.a",
    });
    expect(treeRowKey("root.platform.devices.a")).toBe("root.platform.devices.a");
  });
});

describe("buildObjectTree group refs", () => {
  it("attaches group members under group context path", () => {
    const group: ObjectSummary = {
      id: "g",
      path: "root.platform.devices.group",
      type: "VISUAL_GROUP",
      displayName: "Group",
      description: "",
      templateId: null,
      createdAt: "",
      sortOrder: 0,
      variableNames: [],
      eventNames: [],
    };
    const member: ObjectSummary = {
      id: "m",
      path: "root.platform.devices.member",
      type: "DEVICE",
      displayName: "Member",
      description: "",
      templateId: null,
      createdAt: "",
      sortOrder: 0,
      variableNames: [],
      eventNames: [],
      groupRef: true,
      groupContextPath: group.path,
    };
    const parent: ObjectSummary = {
      id: "p",
      path: "root.platform.devices",
      type: "DEVICES",
      displayName: "Devices",
      description: "",
      templateId: null,
      createdAt: "",
      sortOrder: 0,
      variableNames: [],
      eventNames: [],
    };
    const root: ObjectSummary = {
      id: "r",
      path: "root",
      type: "ROOT",
      displayName: "Root",
      description: "",
      templateId: null,
      createdAt: "",
      sortOrder: 0,
      variableNames: [],
      eventNames: [],
    };
    const platform: ObjectSummary = {
      id: "pl",
      path: "root.platform",
      type: "PLATFORM",
      displayName: "Platform",
      description: "",
      templateId: null,
      createdAt: "",
      sortOrder: 0,
      variableNames: [],
      eventNames: [],
    };
    const tree = buildObjectTree([root, platform, parent, group, member]);
    const devicesNode = tree[0]?.children[0]?.children.find((n) => n.object.path === parent.path);
    expect(devicesNode).toBeDefined();
    const groupNode = devicesNode!.children.find((n) => n.object.path === group.path);
    expect(groupNode).toBeDefined();
    expect(groupNode!.children).toHaveLength(1);
    expect(groupNode!.children[0].object.path).toBe(member.path);
    expect(groupNode!.children[0].object.groupRef).toBe(true);
  });
});
