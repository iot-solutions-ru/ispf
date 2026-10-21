import { describe, expect, it } from "vitest";
import type { ObjectSummary, VariableDto } from "../../types";
import {
  evaluateMarkerRules,
  evaluateMarkerWhen,
  filterMapCatalogChildren,
  hasMarkerRules,
  parseMarkerRules,
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

function vars(entries: Record<string, unknown>): VariableDto[] {
  return Object.entries(entries).map(([name, value]) => ({
    name,
    value: {
      schema: { name, fields: [] },
      rows: [{ value }],
    },
    readable: true,
    writable: false,
    updatedAt: "2026-01-01T00:00:00.000Z",
    historyEnabled: false,
    historyRetentionDays: null,
  })) as VariableDto[];
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
    expect(
      resolveMapMarkerMode({
        markerMode: "status-color",
        statusVariable: "driverStatus",
        markerRulesJson:
          '[{"when":{"variable":"driverStatus","op":"eq","value":"RUNNING"},"style":{"type":"color","color":"#0f0"}}]',
      })
    ).toBe("default");
    expect(resolveMapPopupMode({})).toBe("label");
    expect(resolveMapPopupMode({ popupVariablesJson: "[]" })).toBe("variables");
    expect(resolveMapPopupMode({ popupMode: "none", popupVariablesJson: "[]" })).toBe("none");
  });

  it("parses marker rules and skips invalid rows", () => {
    const rules = parseMarkerRules(`[
      {"when":{"variable":"driverStatus","op":"eq","value":"RUNNING"},"style":{"type":"color","color":"#3fb950"}},
      {"when":{"variable":"","op":"eq","value":"x"},"style":{"type":"color","color":"#f00"}},
      {"when":{"variable":"reg1","op":"gt","value":100},"style":{"type":"icon","url":"/a.svg","size":28}}
    ]`);
    expect(rules).toHaveLength(2);
    expect(rules[0].style).toEqual({ type: "color", color: "#3fb950" });
    expect(rules[1].style).toEqual({ type: "icon", url: "/a.svg", size: 28 });
    expect(hasMarkerRules({ markerRulesJson: "[]" })).toBe(false);
    expect(hasMarkerRules({ markerRulesJson: JSON.stringify(rules) })).toBe(true);
  });

  it("keeps icon rule with empty URL (editor Color→Picture switch)", () => {
    const rules = parseMarkerRules(
      '[{"when":{"variable":"driverStatus","op":"eq","value":"RUNNING"},"style":{"type":"icon","url":""}}]'
    );
    expect(rules).toHaveLength(1);
    expect(rules[0].style).toEqual({ type: "icon", url: "" });
  });

  it("evaluates eq/gt/in and first-match wins", () => {
    const running = vars({ driverStatus: "RUNNING", reg1: 150 });
    expect(
      evaluateMarkerWhen(running, { variable: "driverStatus", op: "eq", value: "RUNNING" })
    ).toBe(true);
    expect(evaluateMarkerWhen(running, { variable: "reg1", op: "gt", value: 100 })).toBe(true);
    expect(
      evaluateMarkerWhen(running, { variable: "driverStatus", op: "in", value: "RUNNING,ONLINE" })
    ).toBe(true);

    const style = evaluateMarkerRules(running, [
      {
        when: { variable: "driverStatus", op: "eq", value: "RUNNING" },
        style: { type: "color", color: "#3fb950" },
      },
      {
        when: { variable: "reg1", op: "gt", value: 100 },
        style: { type: "icon", url: "/hot.svg" },
      },
    ]);
    expect(style).toEqual({ type: "color", color: "#3fb950" });

    const error = vars({ driverStatus: "ERROR", reg1: 150 });
    const second = evaluateMarkerRules(error, [
      {
        when: { variable: "driverStatus", op: "eq", value: "RUNNING" },
        style: { type: "color", color: "#3fb950" },
      },
      {
        when: { variable: "reg1", op: "gt", value: 100 },
        style: { type: "icon", url: "/hot.svg" },
      },
    ]);
    expect(second).toEqual({ type: "icon", url: "/hot.svg" });

    expect(evaluateMarkerRules(vars({}), [])).toBeNull();
    expect(
      evaluateMarkerRules(vars({ driverStatus: "STOPPED" }), [
        {
          when: { variable: "driverStatus", op: "eq", value: "RUNNING" },
          style: { type: "color", color: "#0f0" },
        },
      ])
    ).toBeNull();
  });
});
