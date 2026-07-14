import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { useVariablesQuery } from "./useVariablesQuery";
import * as api from "../api";
import {
  isObjectWebSocketConnected,
  subscribeObjectWebSocketConnection,
} from "./useObjectWebSocket";

vi.mock("../api", () => ({
  fetchVariables: vi.fn(),
}));

vi.mock("./useObjectWebSocket", () => ({
  isObjectWebSocketConnected: vi.fn(() => false),
  subscribeObjectWebSocketConnection: vi.fn(() => () => {}),
  useObjectPathsSubscription: vi.fn(),
  useObjectVariableSubscriptions: vi.fn(),
}));

describe("useVariablesQuery", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.mocked(api.fetchVariables).mockResolvedValue([
      {
        name: "temperature",
        value: {
          schema: { name: "temperature", fields: [{ name: "value", type: "DOUBLE" }] },
          rows: [{ value: 21.5 }],
        },
        readable: true,
        writable: true,
        updatedAt: "2026-06-30T00:00:00.000Z",
        historyEnabled: false,
        historyRetentionDays: null,
      },
    ]);
    vi.mocked(isObjectWebSocketConnected).mockReturnValue(false);
  });

  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }

  it("loads variables for an object path", async () => {
    const path = "root.platform.devices.lab-sensor";
    const { result } = renderHook(() => useVariablesQuery(path, 5000, true), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0]?.name).toBe("temperature");
    expect(api.fetchVariables).toHaveBeenCalledWith(path);
  });

  it("refetches after VARIABLE_UPDATED invalidates the query", async () => {
    const path = "root.platform.devices.lab-sensor";
    const { result } = renderHook(() => useVariablesQuery(path, 60_000, true), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const callsBeforeInvalidate = vi.mocked(api.fetchVariables).mock.calls.length;

    vi.mocked(api.fetchVariables).mockResolvedValueOnce([
      {
        name: "temperature",
        value: {
          schema: { name: "temperature", fields: [{ name: "value", type: "DOUBLE" }] },
          rows: [{ value: 88.0 }],
        },
        readable: true,
        writable: true,
        updatedAt: "2026-06-30T00:00:01.000Z",
        historyEnabled: false,
        historyRetentionDays: null,
      },
    ]);

    await queryClient.invalidateQueries({ queryKey: ["variables", path] });
    await waitFor(() =>
      expect(vi.mocked(api.fetchVariables).mock.calls.length).toBeGreaterThan(callsBeforeInvalidate),
    );
  });

  it("registers websocket connection subscription", () => {
    renderHook(() => useVariablesQuery("root.platform.devices.lab-sensor", 5000, true), { wrapper });
    expect(subscribeObjectWebSocketConnection).toHaveBeenCalled();
  });
});
