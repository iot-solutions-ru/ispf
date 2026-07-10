import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { useAnalyticsCatalog } from "./useAnalyticsCatalog";
import * as analyticsCatalogApi from "../api/analyticsCatalog";

vi.mock("../api/analyticsCatalog", () => ({
  fetchAnalyticsCatalog: vi.fn(),
  fetchAnalyticsCatalogById: vi.fn(),
  validateAnalyticsCatalogExpression: vi.fn(),
}));

describe("useAnalyticsCatalog", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
  });

  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }

  it("maps remote historian catalog entries", async () => {
    vi.mocked(analyticsCatalogApi.fetchAnalyticsCatalog).mockResolvedValue([
      {
        id: "rollingAvg",
        displayName: "Rolling average",
        tier: "A",
        kinds: ["historian"],
        syntax: "rollingAvg(source, window)",
        parameters: [
          {
            name: "source",
            type: "string",
            required: true,
            description: "Source",
            defaultValue: "'temperature'",
          },
          {
            name: "window",
            type: "string",
            required: true,
            description: "Window",
            defaultValue: "'5m'",
          },
        ],
        description: "Historian helper",
        examples: ["rollingAvg('temperature', '5m')"],
        tags: ["historian"],
        pack: "core",
        docAnchor: "rollingAvg",
      },
    ]);

    const { result } = renderHook(() => useAnalyticsCatalog("historian"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.hasRemoteEntries).toBe(true);
    expect(result.current.entries[0]?.id).toBe("rollingAvg");
    expect(result.current.entries[0]?.snippet).toContain("rollingAvg");
  });

  it("falls back to static historian entries when catalog request fails", async () => {
    vi.mocked(analyticsCatalogApi.fetchAnalyticsCatalog).mockRejectedValue(new Error("offline"));
    const { result } = renderHook(() => useAnalyticsCatalog("historian"), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.hasRemoteEntries).toBe(false);
    expect(result.current.entries.length).toBeGreaterThan(0);
    expect(result.current.entries.some((entry) => entry.id === "rollingAvg")).toBe(true);
  });
});
