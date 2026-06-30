import { describe, expect, it } from "vitest";
import {
  appendBundleObject,
  listBundleSectionRows,
  removeBundleSectionItem,
} from "./bundleManifestSections";

describe("bundleManifestSections", () => {
  it("lists and removes manifest section rows", () => {
    const manifest = {
      objects: [{ parentPath: "root.platform.devices", name: "demo", type: "DEVICE" }],
      reports: [{ reportId: "ready-items", title: "Ready" }],
    };
    const rows = listBundleSectionRows(manifest);
    expect(rows).toHaveLength(2);
    const next = removeBundleSectionItem(manifest, "reports", 0);
    expect(listBundleSectionRows(next)).toHaveLength(1);
  });

  it("appends object entry", () => {
    const manifest = { version: "1.0.0" };
    const next = appendBundleObject(manifest, {
      parentPath: "root.platform.devices",
      name: "sensor-02",
      type: "DEVICE",
    });
    expect(next.objects).toHaveLength(1);
  });
});
