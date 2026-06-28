import { describe, expect, it } from "vitest";
import {
  manifestChartToWidget,
  manifestMapToWidget,
  parseManifestSelectionJson,
  resolveManifestScreenKind,
  type OperatorManifestScreen,
} from "./operatorManifest";

describe("operatorManifest screen kinds", () => {
  const base: OperatorManifestScreen = { id: "s1", title: "Screen" };

  it("detects dashboard screen", () => {
    const screen: OperatorManifestScreen = {
      ...base,
      dashboard: { dashboardPath: "root.platform.dashboards.demo-sensor" },
    };
    expect(resolveManifestScreenKind(screen)).toBe("dashboard");
  });

  it("detects chart screen", () => {
    const screen: OperatorManifestScreen = {
      ...base,
      chart: {
        objectPath: "root.platform.devices.demo-sensor-01",
        variableName: "temperature",
      },
    };
    expect(resolveManifestScreenKind(screen)).toBe("chart");
    const widget = manifestChartToWidget(screen, screen.chart!);
    expect(widget.type).toBe("chart");
    expect(widget.variableName).toBe("temperature");
  });

  it("detects map screen", () => {
    const screen: OperatorManifestScreen = {
      ...base,
      map: { parentPath: "root.platform.devices" },
    };
    expect(resolveManifestScreenKind(screen)).toBe("map");
    const widget = manifestMapToWidget(screen, screen.map!);
    expect(widget.parentPath).toBe("root.platform.devices");
  });

  it("prefers dashboard over report", () => {
    const screen: OperatorManifestScreen = {
      ...base,
      dashboard: { dashboardPath: "root.platform.dashboards.demo-sensor" },
      report: { reportId: "ready-items" },
    };
    expect(resolveManifestScreenKind(screen)).toBe("dashboard");
  });

  it("parses selection JSON", () => {
    expect(parseManifestSelectionJson('{"device":"root.platform.devices.demo-sensor-01"}')).toEqual({
      device: "root.platform.devices.demo-sensor-01",
    });
    expect(parseManifestSelectionJson("not-json")).toEqual({});
  });
});
