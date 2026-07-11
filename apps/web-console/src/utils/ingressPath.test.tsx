// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import { HMI_INGRESS_PREFIX, resolveIngressPath } from "./ingressPath";

describe("resolveIngressPath", () => {
  afterEach(() => {
    window.history.replaceState({}, "", "/");
  });

  it("keeps admin paths unchanged", () => {
    window.history.replaceState({}, "", "/");
    expect(resolveIngressPath("/api/v1/info")).toBe("/api/v1/info");
    expect(resolveIngressPath("/ws/objects")).toBe("/ws/objects");
  });

  it("prefixes operator REST and WS with /hmi", () => {
    window.history.replaceState({}, "", "/?mode=operator&app=demo");
    expect(resolveIngressPath("/api/v1/objects")).toBe(`${HMI_INGRESS_PREFIX}/api/v1/objects`);
    expect(resolveIngressPath("/ws/objects")).toBe(`${HMI_INGRESS_PREFIX}/ws/objects`);
  });

  it("does not double-prefix", () => {
    window.history.replaceState({}, "", "/?mode=operator&app=demo");
    expect(resolveIngressPath(`${HMI_INGRESS_PREFIX}/api/v1/info`)).toBe(`${HMI_INGRESS_PREFIX}/api/v1/info`);
  });
});
