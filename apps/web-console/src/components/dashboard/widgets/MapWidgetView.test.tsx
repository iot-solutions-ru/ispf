import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { cleanup, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React, { forwardRef, type CSSProperties, type ReactNode } from "react";
import MapWidgetView from "./MapWidgetView";
import { newWidget } from "../../../types/dashboard";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import * as api from "../../../api";

vi.mock("../../../map/maplibreSetup", () => ({}));

vi.mock("react-map-gl/maplibre", () => {
  const Map = forwardRef(function MockMap(
    {
      children,
      ...props
    }: { children?: ReactNode; mapStyle?: unknown; style?: CSSProperties },
    _ref: unknown,
  ) {
    return (
      <div data-testid="maplibre-map" data-has-style={props.mapStyle != null ? "1" : "0"}>
        {children}
      </div>
    );
  });
  return {
    __esModule: true,
    default: Map,
    Marker: ({
      children,
      longitude,
      latitude,
      onClick,
    }: {
      children?: ReactNode;
      longitude: number;
      latitude: number;
      onClick?: (e: { originalEvent: { stopPropagation: () => void } }) => void;
    }) => (
      <div
        data-testid="map-marker"
        data-lon={String(longitude)}
        data-lat={String(latitude)}
        onClick={() => onClick?.({ originalEvent: { stopPropagation: () => undefined } })}
      >
        {children}
      </div>
    ),
    Popup: ({ children }: { children?: ReactNode }) => (
      <div data-testid="map-popup">{children}</div>
    ),
  };
});

vi.mock("../../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../api")>();
  return {
    ...actual,
    fetchObjects: vi.fn(),
    fetchVariablesBatch: vi.fn(),
  };
});

function renderMap(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return renderWithDashboard(
    <QueryClientProvider client={client}>{ui as ReactNode}</QueryClientProvider>,
  );
}

describe("MapWidgetView", () => {
  beforeEach(() => {
    vi.mocked(api.fetchObjects).mockResolvedValue([
      {
        path: "root.platform.devices.truck-1",
        name: "truck-1",
        displayName: "Truck 1",
        type: "DEVICE",
        children: [],
      },
    ] as never);
    vi.mocked(api.fetchVariablesBatch).mockResolvedValue({
      "root.platform.devices.truck-1": [
        {
          name: "coordinates",
          value: {
            schema: { name: "coordinates", fields: [] },
            rows: [{ latitude: 55.76, longitude: 37.64 }],
          },
          readable: true,
          writable: false,
          updatedAt: "2026-08-02T00:00:00.000Z",
          historyEnabled: false,
          historyRetentionDays: null,
        },
      ],
    } as never);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows parent path hint when parentPath is empty", () => {
    const widget = {
      ...newWidget("map", 0),
      title: "Fleet map",
      parentPath: "",
    };
    renderMap(<MapWidgetView widget={widget} refreshIntervalMs={5000} />);
    expect(screen.getByText(/Specify parentPath/i)).toBeInTheDocument();
  });

  it("renders map canvas and GPS marker for child objects", async () => {
    const widget = {
      ...newWidget("map", 0),
      title: "Fleet map",
      parentPath: "root.platform.devices",
      latVariable: "coordinates",
      latField: "latitude",
      lonField: "longitude",
      centerLat: 55.75,
      centerLon: 37.62,
      zoom: 10,
    };

    renderMap(<MapWidgetView widget={widget} refreshIntervalMs={5000} />);

    await waitFor(() => {
      expect(screen.getByTestId("maplibre-map")).toBeInTheDocument();
    });
    expect(screen.getByTestId("maplibre-map")).toHaveAttribute("data-has-style", "1");

    await waitFor(() => {
      expect(screen.getByTestId("map-marker")).toBeInTheDocument();
    });
    const marker = screen.getByTestId("map-marker");
    expect(marker).toHaveAttribute("data-lat", "55.76");
    expect(marker).toHaveAttribute("data-lon", "37.64");
    expect(screen.getByRole("button", { name: "Truck 1" })).toBeInTheDocument();
  });
});
