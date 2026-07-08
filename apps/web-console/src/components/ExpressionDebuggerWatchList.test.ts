import { describe, expect, it } from "vitest";
import { resolvePinnedWatchValues } from "../components/ExpressionDebuggerWatchList";

describe("resolvePinnedWatchValues", () => {
  it("prefers live variable values over step bindings", () => {
    const values = resolvePinnedWatchValues(
      ["temp"],
      [
        {
          phase: "variable-context",
          status: "ok",
          detail: { temp: 10 },
        },
      ],
      1,
      [{ name: "temp", value: 42 } as never]
    );
    expect(values.temp).toBe(42);
  });

  it("reads bindings from revealed steps when live value is absent", () => {
    const values = resolvePinnedWatchValues(
      ["pressure"],
      [
        {
          phase: "cel-bindings",
          status: "ok",
          detail: { self: { pressure: 1.2 } },
        },
      ],
      1,
      []
    );
    expect(values.pressure).toBe(1.2);
  });

  it("ignores bindings from steps not yet revealed", () => {
    const values = resolvePinnedWatchValues(
      ["flow"],
      [
        { phase: "validate", status: "ok" },
        {
          phase: "variable-context",
          status: "ok",
          detail: { flow: 99 },
        },
      ],
      1,
      []
    );
    expect(values.flow).toBeUndefined();
  });
});
