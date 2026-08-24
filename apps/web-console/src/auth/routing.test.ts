/** @vitest-environment jsdom */
import { describe, expect, it } from "vitest";
import type { AuthSession } from "./session";
import {
  resolveInitialAppMode,
  resolveOperatorAppId,
  shouldOpenOperatorShell,
} from "./routing";

const adminSession: AuthSession = {
  token: "t",
  username: "admin",
  displayName: "Admin",
  roles: ["admin"],
  autoStartEnabled: true,
  autoStartApp: "alarm-console",
};

const operatorSession: AuthSession = {
  token: "t",
  username: "op1",
  displayName: "Operator",
  roles: ["operator"],
  autoStartEnabled: true,
  autoStartApp: "demo",
};

function withSearch(search: string): void {
  window.history.replaceState({}, "", search || "/");
}

describe("resolveInitialAppMode", () => {
  it("defaults configurators to admin even when auto-start is configured", () => {
    withSearch("/");
    expect(resolveInitialAppMode(adminSession)).toBe("admin");
  });

  it("honours explicit operator deep link for configurators", () => {
    withSearch("/?mode=operator&app=demo");
    expect(resolveInitialAppMode(adminSession)).toBe("operator");
  });

  it("defaults pure operator role to operator shell", () => {
    withSearch("/");
    expect(resolveInitialAppMode(operatorSession)).toBe("operator");
  });
});

describe("shouldOpenOperatorShell", () => {
  it("keeps configurators in admin shell when appMode is admin", () => {
    withSearch("/?mode=admin");
    expect(shouldOpenOperatorShell(adminSession, "admin")).toBe(false);
  });

  it("opens operator shell for explicit operator mode", () => {
    withSearch("/?mode=operator&app=demo");
    expect(shouldOpenOperatorShell(adminSession, "operator")).toBe(true);
  });

  it("opens operator shell for operator-only users", () => {
    withSearch("/");
    expect(shouldOpenOperatorShell(operatorSession, "operator")).toBe(true);
  });
});

describe("resolveOperatorAppId", () => {
  it("prefers URL app over session auto-start", () => {
    withSearch("/?app=demo");
    expect(resolveOperatorAppId(adminSession)).toBe("demo");
  });
});
