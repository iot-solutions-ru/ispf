import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { cleanup, screen } from "@testing-library/react";
import ValueWidgetView from "./ValueWidgetView";
import { newWidget } from "../../../types/dashboard";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import * as useBoundVariableModule from "../../../hooks/useBoundVariable";
import * as useWidgetObjectPathModule from "../../../hooks/useWidgetObjectPath";

vi.mock("../../../hooks/useBoundVariable", () => ({
  useBoundVariable: vi.fn(),
}));

vi.mock("../../../hooks/useWidgetObjectPath", () => ({
  useWidgetObjectPath: vi.fn(),
}));

describe("ValueWidgetView", () => {
  beforeEach(() => {
    vi.mocked(useWidgetObjectPathModule.useWidgetObjectPath).mockReturnValue(
      "root.platform.devices.lab-sensor",
    );
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders formatted numeric value with unit", () => {
    vi.mocked(useBoundVariableModule.useBoundVariable).mockReturnValue({
      rawValue: 42.567,
      variable: {
        name: "temperature",
        value: {
          schema: { name: "temperature", fields: [] },
          rows: [{ value: 42.567, unit: "°C" }],
        },
        readable: true,
        writable: false,
        updatedAt: "2026-06-30T00:00:00.000Z",
        historyEnabled: false,
        historyRetentionDays: null,
      },
      isLoading: false,
      isError: false,
    });

    const widget = {
      ...newWidget("value", 0),
      title: "Temperature",
      variableName: "temperature",
      decimals: 2,
      unit: "°C",
    };

    renderWithDashboard(<ValueWidgetView widget={widget} refreshIntervalMs={5000} />);

    expect(screen.getByText("42.57")).toBeInTheDocument();
    expect(screen.getByText("°C")).toBeInTheDocument();
  });

  it("shows select-device hint when selection is required", () => {
    vi.mocked(useWidgetObjectPathModule.useWidgetObjectPath).mockReturnValue("");
    vi.mocked(useBoundVariableModule.useBoundVariable).mockReturnValue({
      rawValue: undefined,
      variable: undefined,
      isLoading: false,
      isError: false,
    });

    const widget = {
      ...newWidget("value", 0),
      title: "Value",
      selectionKey: "device",
      variableName: "temperature",
    };

    renderWithDashboard(<ValueWidgetView widget={widget} refreshIntervalMs={5000} />);

    expect(screen.getByText("Select a device")).toBeInTheDocument();
  });
});
