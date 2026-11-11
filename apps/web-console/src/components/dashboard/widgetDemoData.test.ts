import { describe, expect, it } from "vitest";
import { newWidget } from "../../types/dashboard";
import { buildChartBubbleSampleWidget, buildChartRadarSampleWidget } from "./widgetSamples";
import { editorDemoPreview, useEditorDemoRows } from "./widgetDemoData";

const PALETTE_TYPES = [
  "chart",
  "sparkline",
  "event-feed",
  "work-queue",
  "pie-chart",
  "gantt-chart",
  "network-graph",
] as const;

describe("palette widgets", () => {
  it("does not store demoPreviewJson", () => {
    for (const type of PALETTE_TYPES) {
      expect(newWidget(type, 0).demoPreviewJson).toBeUndefined();
    }
    expect(buildChartBubbleSampleWidget(0).demoPreviewJson).toBeUndefined();
    expect(buildChartRadarSampleWidget(0).demoPreviewJson).toBeUndefined();
  });
});

describe("editorDemoPreview", () => {
  it("uses a built-in payload in the editor when the widget has no demo JSON and no live rows", () => {
    const widget = newWidget("pie-chart", 0);
    const preview = useEditorDemoRows(widget, [], true);
    expect(preview.isDemo).toBe(true);
    expect(preview.rows).toEqual([
      { name: "Готово", value: 45 },
      { name: "В работе", value: 30 },
      { name: "Ожидание", value: 25 },
    ]);
  });

  it("keeps live rows and stays quiet for the operator", () => {
    const widget = newWidget("pie-chart", 0);
    const live = [{ name: "Pump", value: 1 }];
    expect(useEditorDemoRows(widget, live, true)).toEqual({ rows: live, isDemo: false });
    expect(useEditorDemoRows(widget, [], false)).toEqual({ rows: [], isDemo: false });
  });

  it("prefers an explicit demoPreviewJson over the built-in payload", () => {
    const widget = {
      ...newWidget("chart", 0),
      demoPreviewJson: JSON.stringify([{ t: 1, v: 9 }]),
    };
    expect(editorDemoPreview<Array<{ t: number; v: number }>>(widget)).toEqual([{ t: 1, v: 9 }]);
  });

  it("picks the bubble and radar payloads from chartType", () => {
    const bubble = { ...newWidget("chart", 0), chartType: "bubble" as const };
    const radar = { ...newWidget("chart", 0), chartType: "radar" as const };
    expect(editorDemoPreview<Array<{ name: string }>>(bubble)?.map((point) => point.name)).toEqual([
      "A",
      "B",
      "C",
    ]);
    expect(editorDemoPreview<Array<{ subject: string }>>(radar)?.map((row) => row.subject)).toEqual([
      "Temp °C",
      "Temp %",
      "Порог",
    ]);
  });

  it("ignores a demo JSON value that is not a list of rows", () => {
    const widget = { ...newWidget("pie-chart", 0), demoPreviewJson: JSON.stringify("abc") };
    expect(useEditorDemoRows(widget, [], true)).toEqual({ rows: [], isDemo: false });
  });

  it("returns null for a widget type that has no preview", () => {
    expect(editorDemoPreview(newWidget("value", 0))).toBeNull();
  });
});
