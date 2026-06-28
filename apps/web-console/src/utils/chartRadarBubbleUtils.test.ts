import { describe, expect, it } from "vitest";
import {
  buildBubbleSnapshotPoints,
  buildRadarRows,
  parseBubblePointsJson,
  parseDemoBubblePoints,
  parseDemoRadarRows,
  parseRadarAxesJson,
  zipBubbleTrajectoryPoints,
} from "./chartRadarBubbleUtils";
import type { VariableDto } from "../types";

const valueRecord = (value: number) =>
  ({
    schema: { name: "test", fields: [] },
    rows: [{ value }],
  }) as VariableDto["value"];

const variables: VariableDto[] = [
  {
    name: "temperature",
    readable: true,
    writable: false,
    updatedAt: "",
    historyEnabled: true,
    historyRetentionDays: 7,
    value: valueRecord(22.5),
  },
  {
    name: "humidity",
    readable: true,
    writable: false,
    updatedAt: "",
    historyEnabled: true,
    historyRetentionDays: 7,
    value: valueRecord(55),
  },
  {
    name: "power",
    readable: true,
    writable: false,
    updatedAt: "",
    historyEnabled: true,
    historyRetentionDays: 7,
    value: valueRecord(120),
  },
];

describe("chartRadarBubbleUtils", () => {
  it("parses bubble points JSON", () => {
    const configs = parseBubblePointsJson(
      JSON.stringify([{ label: "A", xVariable: "temperature", yVariable: "humidity" }])
    );
    expect(configs).toHaveLength(1);
    expect(configs[0]?.label).toBe("A");
  });

  it("builds bubble snapshot from variables", () => {
    const configs = parseBubblePointsJson(
      JSON.stringify([
        {
          label: "Sensor",
          xVariable: "temperature",
          yVariable: "humidity",
          sizeVariable: "power",
        },
      ])
    );
    const points = buildBubbleSnapshotPoints(configs, variables, 40);
    expect(points).toEqual([{ name: "Sensor", x: 22.5, y: 55, z: 120 }]);
  });

  it("zips x/y trend into bubble trajectory", () => {
    const xSeries = [
      { t: 1, time: "10:00", value: 10 },
      { t: 2, time: "10:01", value: 12 },
    ];
    const ySeries = [
      { t: 1, time: "10:00", value: 20 },
      { t: 2, time: "10:01", value: 24 },
    ];
    const points = zipBubbleTrajectoryPoints(xSeries, ySeries, null, 50);
    expect(points).toHaveLength(2);
    expect(points[0]).toMatchObject({ x: 10, y: 20, z: 50 });
  });

  it("parses radar axes and builds rows", () => {
    const axes = parseRadarAxesJson(
      JSON.stringify([
        { label: "Temp", variableName: "temperature", max: 50 },
        { label: "Hum", variableName: "humidity" },
      ])
    );
    expect(axes).toHaveLength(2);
    const rows = buildRadarRows(axes, variables);
    expect(rows).toEqual([
      { subject: "Temp", value: 22.5, fullMark: 50 },
      { subject: "Hum", value: 55, fullMark: 100 },
    ]);
  });

  it("parses demo preview payloads", () => {
    expect(parseDemoBubblePoints([{ x: 1, y: 2, z: 30, name: "A" }])).toHaveLength(1);
    expect(parseDemoRadarRows([{ subject: "Temp", value: 40, fullMark: 100 }])).toHaveLength(1);
  });
});
