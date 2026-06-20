import { describe, expect, it } from "vitest";
import { parseApiError } from "./parseApiError";

describe("parseApiError", () => {
  it("returns ProblemDetail detail when present", () => {
    const json = JSON.stringify({
      type: "about:blank",
      title: "Invalid state",
      status: 409,
      detail: "Variable is read-only: federationProxy",
    });
    expect(parseApiError(json, "fallback")).toBe("Variable is read-only: federationProxy");
  });

  it("falls back to title when detail is missing", () => {
    const json = JSON.stringify({ title: "Bad Gateway", status: 502 });
    expect(parseApiError(json, "fallback")).toBe("Bad Gateway");
  });

  it("returns plain text when body is not JSON", () => {
    expect(parseApiError("Sync catalog failed: 403", "fallback")).toBe("Sync catalog failed: 403");
  });
});
