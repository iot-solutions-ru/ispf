import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchVariables } from "../../../api";
import { newWidget } from "../../../types/dashboard";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import { WidgetTypeSpecificFields } from "./widgetEditorFields";
import type { WidgetFieldContext } from "./widgetFieldPrimitives";

vi.mock("../../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../api")>();
  return { ...actual, fetchVariables: vi.fn() };
});

describe("variable-editor widget fields", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(fetchVariables).mockReset();
  });

  it("loads variable names from the bound object path for the show list", async () => {
    vi.mocked(fetchVariables).mockResolvedValue([
      {
        name: "temperature",
        readable: true,
        writable: true,
        value: null,
        updatedAt: null,
        historyEnabled: false,
        historyRetentionDays: null,
      },
      {
        name: "internal",
        readable: false,
        writable: false,
        value: null,
        updatedAt: null,
        historyEnabled: false,
        historyRetentionDays: null,
      },
    ]);

    const widget = {
      ...newWidget("variable-editor", 0),
      objectPath: "root.platform.devices.demo-sensor-01",
    };
    const ctx: WidgetFieldContext = {
      widget,
      objects: [],
      dashboards: [],
      reports: [],
      variables: [],
      allVariableNames: [],
      variableSelectEnabled: true,
      selectionKeys: [],
      sessionParams: [],
      update: () => {},
    };

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderWithDashboard(
      <QueryClientProvider client={queryClient}>
        <WidgetTypeSpecificFields {...ctx} />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(fetchVariables).toHaveBeenCalledWith("root.platform.devices.demo-sensor-01");
    });

    const listLabel = screen.getByText(/Which variables to show/i);
    const rowSelect = listLabel.parentElement?.querySelector("select");
    expect(rowSelect).toBeTruthy();
    await waitFor(() => {
      expect(rowSelect?.querySelectorAll("option").length).toBeGreaterThan(1);
    });
    expect([...rowSelect!.querySelectorAll("option")].map((option) => option.textContent)).toEqual([
      "—",
      "temperature",
    ]);
  });
});
