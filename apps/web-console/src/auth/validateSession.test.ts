/** @vitest-environment jsdom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthSession } from "./session";
import { setStoredSession } from "./session";
import { fetchWithIngressFallback } from "../utils/ingress/ingressFetch";
import { validateStoredSession } from "./validateSession";

vi.mock("../utils/ingress/ingressFetch", () => ({
  fetchWithIngressFallback: vi.fn(),
  resetIngressRouteCache: vi.fn(),
}));

const session: AuthSession = {
  token: "tok-1",
  username: "alice",
  displayName: "Alice",
  roles: ["operator"],
};

describe("validateStoredSession", () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    vi.mocked(fetchWithIngressFallback).mockReset();
    setStoredSession(session);
  });

  it("merges roles from /auth/me into the stored session", async () => {
    vi.mocked(fetchWithIngressFallback).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ authenticated: true, principal: "alice", roles: ["developer"] }),
    } as Response);

    const next = await validateStoredSession();

    expect(next?.roles).toEqual(["developer"]);
    expect(JSON.parse(sessionStorage.getItem("ispf-auth-session") ?? "{}").roles).toEqual([
      "developer",
    ]);
  });

  it("keeps the stored session when /auth/me roles are unchanged", async () => {
    const dispatch = vi.spyOn(window, "dispatchEvent");
    vi.mocked(fetchWithIngressFallback).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ authenticated: true, principal: "alice", roles: ["operator"] }),
    } as Response);

    const next = await validateStoredSession();

    expect(next?.roles).toEqual(["operator"]);
    const types = dispatch.mock.calls.map((call) => (call[0] as Event).type);
    expect(types).not.toContain("ispf-session-updated");
    dispatch.mockRestore();
  });

  it("clears the session when /auth/me reports unauthenticated", async () => {
    vi.mocked(fetchWithIngressFallback).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ authenticated: false, roles: [] }),
    } as Response);

    await expect(validateStoredSession()).resolves.toBeNull();
    expect(sessionStorage.getItem("ispf-auth-session")).toBeNull();
  });
});
