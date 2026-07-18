// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchWithIngressFallback,
  resetIngressRouteCache,
  resolveIngressFetchPath,
  resolveIngressWebSocketPaths,
  shouldFallbackFromHmiIngress,
  stripHmiIngressPrefix,
} from "./ingressFetch";
import { HMI_INGRESS_PREFIX } from "./ingressPath";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function htmlResponse(status = 200): Response {
  return new Response("<!doctype html><html></html>", {
    status,
    headers: { "Content-Type": "text/html" },
  });
}

describe("ingressFetch", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/?mode=operator&app=demo");
    sessionStorage.clear();
    resetIngressRouteCache();
  });

  afterEach(() => {
    resetIngressRouteCache();
    window.history.replaceState({}, "", "/");
    vi.restoreAllMocks();
  });

  it("strips /hmi prefix for direct path", () => {
    expect(stripHmiIngressPrefix(`${HMI_INGRESS_PREFIX}/api/v1/info`)).toBe("/api/v1/info");
  });

  it("detects SPA/gateway responses as hmi ingress miss", () => {
    expect(shouldFallbackFromHmiIngress(htmlResponse())).toBe(true);
    expect(shouldFallbackFromHmiIngress(htmlResponse(404))).toBe(true);
    expect(shouldFallbackFromHmiIngress(new Response("", { status: 502 }))).toBe(true);
    expect(shouldFallbackFromHmiIngress(jsonResponse({ ok: true }))).toBe(false);
    expect(shouldFallbackFromHmiIngress(jsonResponse({ error: "x" }, 503))).toBe(false);
  });

  it("falls back from /hmi to /api when ingress returns HTML", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(htmlResponse())
      .mockResolvedValueOnce(jsonResponse({ authenticated: true }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await fetchWithIngressFallback("/api/v1/auth/me");
    expect(response.ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe(`${HMI_INGRESS_PREFIX}/api/v1/auth/me`);
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/auth/me");
    expect(resolveIngressFetchPath("/api/v1/info")).toBe("/api/v1/info");
  });

  it("keeps /hmi when ingress responds with JSON", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ version: "1" }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchWithIngressFallback("/api/v1/info");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe(`${HMI_INGRESS_PREFIX}/api/v1/info`);
    expect(resolveIngressFetchPath("/api/v1/info")).toBe(`${HMI_INGRESS_PREFIX}/api/v1/info`);
  });

  it("re-validates cached hmi and falls back when SPA HTML is returned", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ version: "1" }))
      .mockResolvedValueOnce(htmlResponse())
      .mockResolvedValueOnce(jsonResponse({ online: false }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchWithIngressFallback("/api/v1/info");
    expect(sessionStorage.getItem("ispf-ingress-route")).toBe("hmi");

    const response = await fetchWithIngressFallback("/api/v1/objects/variables/batch?paths=x");
    expect(response.ok).toBe(true);
    expect(await response.json()).toEqual({ online: false });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toBe(`${HMI_INGRESS_PREFIX}/api/v1/objects/variables/batch?paths=x`);
    expect(fetchMock.mock.calls[2][0]).toBe("/api/v1/objects/variables/batch?paths=x");
    expect(sessionStorage.getItem("ispf-ingress-route")).toBe("direct");
  });

  it("lists hmi then direct websocket paths in auto mode", () => {
    expect(resolveIngressWebSocketPaths("/ws/objects")).toEqual([
      `${HMI_INGRESS_PREFIX}/ws/objects`,
      "/ws/objects",
    ]);
  });
});
