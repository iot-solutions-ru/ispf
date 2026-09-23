import { useMemo, useState } from "react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ObjectTableWidgetView from "./ObjectTableWidgetView";
import { newWidget } from "../../../types/dashboard";
import type { ObjectTableWidget } from "../../../types/dashboard";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import { DashboardProvider } from "../DashboardContext";
import { emptySession, SELECTION_OWNER_PARAM } from "../useDashboardContext";
import type { DashboardSession } from "../useDashboardContext";
import * as api from "../../../api";

vi.mock("../../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../api")>();
  return {
    ...actual,
    fetchObjects: vi.fn(),
    fetchVariablesBatch: vi.fn(),
  };
});

function table(id: string, title: string): ObjectTableWidget {
  return {
    ...(newWidget("object-table", 0) as ObjectTableWidget),
    id,
    title,
    parentPath: "root.platform.devices",
    selectionKey: "device",
    rowTargetDashboard: undefined,
    rowOpenMode: undefined,
  };
}

function selectedLabels(title: string): string[] {
  const widget = screen.getByText(title).closest(".dash-widget");
  if (!widget) {
    return [];
  }
  return [...widget.querySelectorAll("tbody tr.selected")].map(
    (row) => row.querySelector("td")?.textContent ?? ""
  );
}

function TwoTables({ initial = emptySession() }: { initial?: DashboardSession }) {
  const [session, setSession] = useState<DashboardSession>(initial);
  return (
    <DashboardProvider session={session} onSessionChange={setSession}>
      <ObjectTableWidgetView widget={table("table-a", "Table A")} refreshIntervalMs={60_000} />
      <ObjectTableWidgetView widget={table("table-b", "Table B")} refreshIntervalMs={60_000} />
    </DashboardProvider>
  );
}

function renderTables(initial?: DashboardSession) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  renderWithDashboard(
    <QueryClientProvider client={client}>
      <TwoTables initial={initial} />
    </QueryClientProvider>
  );
}

describe("ObjectTableWidgetView selection", () => {
  beforeEach(() => {
    vi.mocked(api.fetchObjects).mockResolvedValue([
      {
        path: "root.platform.devices.a",
        displayName: "Alpha",
        type: "DEVICE",
      },
      {
        path: "root.platform.devices.b",
        displayName: "Beta",
        type: "DEVICE",
      },
    ] as never);
    vi.mocked(api.fetchVariablesBatch).mockResolvedValue({} as never);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("does not highlight a row until one is clicked", async () => {
    renderTables();

    expect(await screen.findAllByText("Alpha")).toHaveLength(2);
    expect(selectedLabels("Table A")).toEqual([]);
    expect(selectedLabels("Table B")).toEqual([]);
  });

  it("highlights the clicked row only in the table that was clicked", async () => {
    renderTables();

    expect(await screen.findAllByText("Alpha")).toHaveLength(2);

    const tableA = screen.getByText("Table A").closest(".dash-widget");
    const beta = tableA?.querySelector("tbody tr:nth-child(2)");
    expect(beta).toBeTruthy();
    fireEvent.click(beta!);

    expect(selectedLabels("Table A")).toEqual(["Beta"]);
    expect(selectedLabels("Table B")).not.toContain("Beta");
  });

  it("restores a shared selection onto a single table", async () => {
    renderTables({
      ...emptySession(),
      selection: { device: "root.platform.devices.b" },
    });

    await screen.findAllByText("Beta");
    await waitFor(() => {
      const selected = [...selectedLabels("Table A"), ...selectedLabels("Table B")];
      expect(selected.filter((label) => label === "Beta")).toHaveLength(1);
    });
  });

  it("highlights the clicked row when the widget id is empty", async () => {
    function EmptyIdTables() {
      const [session, setSession] = useState<DashboardSession>(emptySession());
      const onSessionChange = (next: DashboardSession) => setSession(next);
      return (
        <DashboardProvider session={session} onSessionChange={onSessionChange}>
          <ObjectTableWidgetView widget={table("", "Table A")} refreshIntervalMs={60_000} />
          <ObjectTableWidgetView widget={table("", "Table B")} refreshIntervalMs={60_000} />
        </DashboardProvider>
      );
    }

    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    renderWithDashboard(
      <QueryClientProvider client={client}>
        <EmptyIdTables />
      </QueryClientProvider>
    );

    expect(await screen.findAllByText("Alpha")).toHaveLength(2);
    const tableA = screen.getByText("Table A").closest(".dash-widget");
    const beta = tableA?.querySelector("tbody tr:nth-child(2)");
    expect(beta).toBeTruthy();
    fireEvent.click(beta!);

    expect(selectedLabels("Table A")).toEqual(["Beta"]);
    expect(selectedLabels("Table B")).toEqual([]);
  });

  it("does not reclaim a selection the parent session refuses to store", async () => {
    function FrozenSession() {
      const [writes, setWrites] = useState(0);
      const session = useMemo<DashboardSession>(
        () => ({
          ...emptySession(),
          selection: { device: "root.platform.devices.b" },
        }),
        []
      );
      const onSessionChange = () => setWrites((count) => count + 1);
      return (
        <DashboardProvider session={session} onSessionChange={onSessionChange}>
          <span data-testid="writes">{writes}</span>
          <ObjectTableWidgetView widget={table("table-a", "Table A")} refreshIntervalMs={60_000} />
        </DashboardProvider>
      );
    }

    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    renderWithDashboard(
      <QueryClientProvider client={client}>
        <FrozenSession />
      </QueryClientProvider>
    );

    await screen.findByText("Beta");
    await waitFor(() => {
      expect(screen.getByTestId("writes").textContent).toBe("1");
    });
  });

  it("keeps the saved table highlighted after the session is restored", async () => {
    renderTables({
      ...emptySession(),
      selection: { device: "root.platform.devices.b" },
      params: {
        [SELECTION_OWNER_PARAM]: {
          device: { path: "root.platform.devices.b", ownerId: "table-a" },
        },
      },
    });

    await screen.findAllByText("Beta");
    await waitFor(() => {
      expect(selectedLabels("Table A")).toEqual(["Beta"]);
      expect(selectedLabels("Table B")).toEqual([]);
    });
  });
});
