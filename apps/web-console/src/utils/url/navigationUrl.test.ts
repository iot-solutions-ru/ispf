import { describe, expect, it } from "vitest";
import { isSafeNavigationUrl } from "./navigationUrl";

describe("isSafeNavigationUrl", () => {
  it("allows http(s) and relative paths", () => {
    expect(isSafeNavigationUrl("https://example.com/x?y=1")).toBe(true);
    expect(isSafeNavigationUrl("http://example.com")).toBe(true);
    expect(isSafeNavigationUrl("/dashboards/main")).toBe(true);
    expect(isSafeNavigationUrl("./report")).toBe(true);
    expect(isSafeNavigationUrl("../up")).toBe(true);
    expect(isSafeNavigationUrl("dashboards/main")).toBe(true);
  });

  it("rejects script and data schemes", () => {
    expect(isSafeNavigationUrl("javascript:alert(1)")).toBe(false);
    expect(isSafeNavigationUrl("JavaScript:alert(1)")).toBe(false);
    expect(isSafeNavigationUrl("data:text/html,<script>alert(1)</script>")).toBe(false);
    expect(isSafeNavigationUrl("vbscript:msgbox(1)")).toBe(false);
    expect(isSafeNavigationUrl("mailto:a@b.c")).toBe(false);
  });

  it("rejects schemes hidden behind whitespace/control chars", () => {
    expect(isSafeNavigationUrl("  javascript:alert(1)")).toBe(false);
    expect(isSafeNavigationUrl("java\tscript:alert(1)")).toBe(false);
    expect(isSafeNavigationUrl("java\nscript:alert(1)")).toBe(false);
  });

  it("rejects empty and missing urls", () => {
    expect(isSafeNavigationUrl("")).toBe(false);
    expect(isSafeNavigationUrl("   ")).toBe(false);
    expect(isSafeNavigationUrl(null)).toBe(false);
    expect(isSafeNavigationUrl(undefined)).toBe(false);
  });
});
