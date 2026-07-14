import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { applyObjectWebSocketMessage } from "./objectWebSocketCache";
import type { ObjectWsMessage } from "./objectWebSocketTypes";
import type { DataRecord, VariableDto } from "../types";

vi.mock("./workQueueCache", () => ({
  refreshWorkQueue: vi.fn(),
}));

vi.mock("../api", () => ({
  fetchVariables: vi.fn(),
}));

import { fetchVariables } from "../api";

const sampleValue: DataRecord = {
  schema: { name: "temperature", fields: [{ name: "value", type: "DOUBLE" }] },
  rows: [{ value: 21.5 }],
};

function variableUpdated(
  path: string,
  variableName: string,
  value?: DataRecord,
): ObjectWsMessage {
  return {
    type: "VARIABLE_UPDATED",
    path,
    variableName,
    timestamp: "2026-06-30T00:00:00.000Z",
    ...(value ? { value } : {}),
  };
}

function vars(...names: Array<[string, number]>): VariableDto[] {
  return names.map(([name, value]) => ({
    name,
    value: {
      schema: { name, fields: [{ name: "value", type: "DOUBLE" }] },
      rows: [{ value }],
    },
    readable: true,
    writable: false,
    updatedAt: "2026-06-29T00:00:00.000Z",
    historyEnabled: false,
    historyRetentionDays: null,
  }));
}

describe("applyObjectWebSocketMessage", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
    vi.mocked(fetchVariables).mockReset();
  });

  it("patches variables and variables-batch from VARIABLE_UPDATED value", () => {
    const path = "root.platform.devices.lab-sensor";
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");
    queryClient.setQueryData(["variables", path], vars(["temperature", 20], ["humidity", 40]));
    queryClient.setQueryData(
      ["variables-batch", [path, "root.other"]],
      {
        [path]: vars(["temperature", 20], ["humidity", 40]),
        "root.other": vars(["status", 1]),
      },
    );

    applyObjectWebSocketMessage(queryClient, variableUpdated(path, "temperature", sampleValue));

    const single = queryClient.getQueryData<VariableDto[]>(["variables", path]);
    expect(single?.find((v) => v.name === "temperature")?.value).toEqual(sampleValue);
    expect(single?.find((v) => v.name === "humidity")?.value?.rows[0].value).toBe(40);

    const batch = queryClient.getQueryData<Record<string, VariableDto[]>>([
      "variables-batch",
      [path, "root.other"],
    ]);
    expect(batch?.[path].find((v) => v.name === "temperature")?.value).toEqual(sampleValue);
    expect(batch?.["root.other"]).toEqual(vars(["status", 1]));

    expect(invalidateQueries).not.toHaveBeenCalledWith({ queryKey: ["variables-batch"] });
    expect(invalidateQueries).not.toHaveBeenCalledWith({ queryKey: ["variables", path] });
    expect(fetchVariables).not.toHaveBeenCalled();
  });

  it("fetches a single path when VARIABLE_UPDATED has no value", async () => {
    const path = "root.platform.devices.lab-sensor";
    const refreshed = vars(["temperature", 99]);
    vi.mocked(fetchVariables).mockResolvedValue(refreshed);
    queryClient.setQueryData(
      ["variables-batch", [path]],
      { [path]: vars(["temperature", 1]) },
    );

    applyObjectWebSocketMessage(queryClient, variableUpdated(path, "temperature"));

    await vi.waitFor(() => {
      expect(fetchVariables).toHaveBeenCalledWith(path);
    });
    await vi.waitFor(() => {
      expect(queryClient.getQueryData(["variables", path])).toEqual(refreshed);
      expect(
        queryClient.getQueryData<Record<string, VariableDto[]>>(["variables-batch", [path]])?.[path],
      ).toEqual(refreshed);
    });
  });

  it("invalidates objects and driver status when driverStatus changes", () => {
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");
    const path = "root.platform.devices.pump-01";
    applyObjectWebSocketMessage(
      queryClient,
      variableUpdated(path, "driverStatus", sampleValue),
    );

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
