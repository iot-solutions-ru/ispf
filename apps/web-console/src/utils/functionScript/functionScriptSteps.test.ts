import { describe, expect, it } from "vitest";
import {
  parseScriptBody,
  scriptHasSqlStep,
  serializeScriptBody,
  serializeStepsArray,
  SCRIPT_TEMPLATES,
  unwrapSteps,
  wrapSteps,
} from "./functionScriptSteps";

describe("functionScriptSteps", () => {
  it("round-trips mqtt ingest template", () => {
    const body = serializeStepsArray(SCRIPT_TEMPLATES.find((t) => t.id === "mqttIngest")!.steps);
    const parsed = parseScriptBody(body);
    expect(parsed.error).toBeUndefined();
    expect(parsed.steps).toHaveLength(7);
    expect(parsed.steps[0].step.type).toBe("jsonParse");
    expect(serializeScriptBody(parsed.steps)).toBe(body);
  });

  it("reports parse errors for invalid JSON", () => {
    const parsed = parseScriptBody("{not json");
    expect(parsed.steps).toHaveLength(0);
    expect(parsed.error).toBeTruthy();
  });

  it("detects SQL steps, including ones nested in a branch", () => {
    expect(scriptHasSqlStep("")).toBe(false);
    expect(scriptHasSqlStep("{not json")).toBe(false);
    expect(scriptHasSqlStep(JSON.stringify({ steps: [{ type: "return", fields: { ok: true } }] }))).toBe(false);
    expect(
      scriptHasSqlStep(
        JSON.stringify({
          steps: [
            {
              type: "when",
              var: "input.id",
              then: [{ type: "selectOne", var: "row", sql: "SELECT 1", params: [] }],
            },
          ],
        }),
      ),
    ).toBe(true);
  });

  it("unwrapSteps omits empty optional fields", () => {
    const steps = wrapSteps([
      { type: "return", fields: { ok: true } },
      { type: "setVar", var: "x", value: "1", expression: undefined as unknown as string },
    ]);
    const out = unwrapSteps(steps);
    expect(out[1]).toEqual({ type: "setVar", var: "x", value: "1" });
  });
});
