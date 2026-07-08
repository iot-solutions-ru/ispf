/** @vitest-environment jsdom */
import { describe, expect, it, vi } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { syncOperatorCachesOnReconnect } from "./operatorOfflineSync";

describe("operatorOfflineSync", () => {
  it("refetches operator manifest, UI, dashboards, and screen queries on reconnect", async () => {
    const queryClient = new QueryClient();
    const refetch = vi.spyOn(queryClient, "refetchQueries").mockResolvedValue([]);

    await syncOperatorCachesOnReconnect(queryClient, "demo-app");

    expect(refetch).toHaveBeenCalledWith({ queryKey: ["operator-manifest", "demo-app"] });
    expect(refetch).toHaveBeenCalledWith({ queryKey: ["operator-ui", "demo-app"] });
    expect(refetch).toHaveBeenCalledWith({ queryKey: ["dashboard"] });
    expect(refetch).toHaveBeenCalledWith({ queryKey: ["variables"] });
    expect(refetch).toHaveBeenCalledWith({ queryKey: ["bff-table"] });
    expect(refetch).toHaveBeenCalledWith({ queryKey: ["app-report", "demo-app"] });
    expect(refetch).toHaveBeenCalledTimes(6);
  });
});
