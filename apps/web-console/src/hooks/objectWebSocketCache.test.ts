import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { applyObjectWebSocketMessage } from "./objectWebSocketCache";
import type { ObjectWsMessage } from "./objectWebSocketTypes";

vi.mock("./workQueueCache", () => ({
  refreshWorkQueue: vi.fn(),
}));

function variableUpdated(path: string, variableName: string): ObjectWsMessage {
  return {
    type: "VARIABLE_UPDATED",
    path,
    variableName,
    timestamp: "2026-06-30T00:00:00.000Z",
  };
}

describe("applyObjectWebSocketMessage", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
  });

  it("invalidates variables cache for VARIABLE_UPDATED", () => {
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");
    const path = "root.platform.devices.lab-sensor";
    applyObjectWebSocketMessage(queryClient, variableUpdated(path, "temperature"));

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["variables", path] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["variables-batch"] });
  });

  it("invalidates batch widgets and driver status when driverStatus changes", () => {
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");
    const path = "root.platform.devices.pump-01";
    applyObjectWebSocketMessage(queryClient, variableUpdated(path, "driverStatus"));

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["variables", path] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["objects"] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["driver-status", path] });
  });

  it("invalidates object tree queries on structural updates", () => {
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");
    const path = "root.platform.devices.new-device";
    applyObjectWebSocketMessage(queryClient, {
      type: "CREATED",
      path,
      variableName: "",
      timestamp: "2026-06-30T00:00:00.000Z",
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["objects"] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["object", path] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["object-editor", path] });
  });

  it("invalidates dashboard layout when object metadata changes", () => {
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");
    const path = "root.platform.dashboards.ops-board";
    applyObjectWebSocketMessage(queryClient, {
      type: "UPDATED",
      path,
      variableName: "",
      timestamp: "2026-06-30T00:00:00.000Z",
      revision: 2,
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["dashboard", path] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["workflow", path] });
  });
});
