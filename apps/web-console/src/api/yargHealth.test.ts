import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchYargHealth } from "./yargHealth";

vi.mock("../auth/session", () => ({
  getAuthHeaders: () => ({ Authorization: "Bearer test" }),
}));

describe("fetchYargHealth", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("requests the platform report health endpoint", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({
        libreOfficeAvailable: false,
        configuredPath: null,
        resolvedPath: null,
        timeoutSeconds: 60,
        pdfHint: "hint",
      }),
    } as Response);

    const health = await fetchYargHealth();

    expect(health).toEqual({
      libreOfficeAvailable: false,
      configuredPath: null,
      resolvedPath: null,
      timeoutSeconds: 60,
      pdfHint: "hint",
    });
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/platform/reports/health", {
      headers: { Authorization: "Bearer test" },
    });
  });

  it("rejects with the response body when the endpoint fails", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue({
      ok: false,
      status: 404,
      text: async () => "not found",
    } as Response);

    await expect(fetchYargHealth()).rejects.toThrow("not found");
  });
});
