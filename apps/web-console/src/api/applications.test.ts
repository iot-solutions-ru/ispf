import { describe, expect, it, vi, beforeEach } from "vitest";
import { listFunctionVersions, rollbackFunction } from "./applications";

describe("applications API", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });
  });

  it("listFunctionVersions calls query params", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => [{ version: "1", active: true }],
    } as Response);

    const rows = await listFunctionVersions("myapp", "root.platform.devices.d1", "listItems");

    expect(rows).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/applications/myapp/functions?objectPath=root.platform.devices.d1&functionName=listItems",
      expect.any(Object)
    );
  });

  it("rollbackFunction posts rollback body", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({
        appId: "myapp",
        objectPath: "root.platform.devices.d1",
        functionName: "listItems",
        version: "1",
        status: "active",
      }),
    } as Response);

    const result = await rollbackFunction(
      "myapp",
      "root.platform.devices.d1",
      "listItems",
      "1"
    );

    expect(result.version).toBe("1");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/applications/myapp/functions/rollback",
      expect.objectContaining({ method: "POST" })
    );
  });
});
