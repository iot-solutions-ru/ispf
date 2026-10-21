import { describe, expect, it } from "vitest";
import type { ObjectSummary } from "../../types";
import {
  filterMapCatalogChildren,
  parsePathListJson,
  resolveMapMarkerMode,
  resolveMapPopupMode,
} from "./mapWidgetUtils";

function obj(path: string, type: ObjectSummary["type"] = "DEVICE"): ObjectSummary {
  const leaf = path.split(".").pop() ?? path;
  return {
    id: path,
    path,
    type,
    displayName: leaf,
    description: "",
    templateId: null,
    createdAt: "2026-01-01T00:00:00.000Z",
    sortOrder: 0,
    variableNames: [],
    eventNames: [],
  };
}

describe("mapWidgetUtils", () => {
  it("parses path list JSON", () => {
    expect(parsePathListJson('["a","b"]')).toEqual(["a", "b"]);
    expect(parsePathListJson("")).toEqual([]);
    expect(parsePathListJson("not-json")).toEqual([]);
  });

  it("filters by namePattern and objectType", () => {
    const list = [
      obj("root.platform.devices.modbus-localhost"),
      obj("root.platform.devices.modbus-localhost-s2"),
      obj("root.platform.devices.demo-sensor-01"),
      obj("root.platform.devices.folder-x", "FOLDER"),
    ];
    const filtered = filterMapCatalogChildren(list, {
      namePattern: "modbus-localhost*",
      objectType: "DEVICE",
    });
    expect(filtered.map((o) => o.path)).toEqual([
      "root.platform.devices.modbus-localhost",
      "root.platform.devices.modbus-localhost-s2",
    ]);
  });

  it("applies include and exclude path lists", () => {
    const list = [
      obj("root.platform.devices.modbus-localhost"),
      obj("root.platform.devices.modbus-localhost-s2"),
      obj("root.platform.devices.modbus-localhost-s3"),
    ];
    const included = filterMapCatalogChildren(list, {
      includePathsJson: '["modbus-localhost-s2","root.platform.devices.modbus-localhost-s3"]',
    });
    expect(included.map((o) => o.path)).toEqual([
      "root.platform.devices.modbus-localhost-s2",
      "root.platform.devices.modbus-localhost-s3",
    ]);
    const excluded = filterMapCatalogChildren(list, {
      excludePathsJson: '["modbus-localhost-s2"]',
    });
    expect(excluded.map((o) => o.path)).toEqual([
      "root.platform.devices.modbus-localhost",
      "root.platform.devices.modbus-localhost-s3",
    ]);
  });

  it("resolves marker and popup modes with legacy fallbacks", () => {
    expect(resolveMapMarkerMode({})).toBe("default");
    expect(resolveMapMarkerMode({ statusVariable: "driverStatus" })).toBe("status-color");
    expect(resolveMapMarkerMode({ markerMode: "icon", statusVariable: "x" })).toBe("icon");
    expect(resolveMapPopupMode({})).toBe("label");
    expect(resolveMapPopupMode({ popupVariablesJson: "[]" })).toBe("variables");
    expect(resolveMapPopupMode({ popupMode: "none", popupVariablesJson: "[]" })).toBe("none");
  });
});
