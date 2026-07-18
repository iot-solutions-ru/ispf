import { describe, expect, it } from "vitest";
import type { DataRecord, VariableDto } from "../../types";
import { applyRemoteVariables } from "./objectPropertiesRemoteSync";

const temperatureSchema = {
  name: "temperature",
  fields: [{ name: "value", type: "DOUBLE" as const }],
};

function record(value: number): DataRecord {
  return {
    schema: temperatureSchema,
    rows: [{ value }],
  };
}

function temperatureDto(value: number): VariableDto {
  return {
    name: "temperature",
    value: record(value),
    readable: true,
    writable: true,
    updatedAt: "2026-06-30T00:00:00.000Z",
    historyEnabled: false,
    historyRetentionDays: null,
  };
}

describe("applyRemoteVariables", () => {
  it("merges live telemetry into clean inspector state", () => {
    const state = { variables: { temperature: record(21.5) } };
    const baseline = { variables: { temperature: record(21.5) } };

    const merged = applyRemoteVariables([temperatureDto(42.0)], state, baseline);

    expect(merged?.state.variables.temperature.rows[0].value).toBe(42.0);
    expect(merged?.baseline.variables.temperature.rows[0].value).toBe(42.0);
  });

  it("does not overwrite variables the operator is editing", () => {
    const state = { variables: { temperature: record(99.0) } };
    const baseline = { variables: { temperature: record(21.5) } };

    const merged = applyRemoteVariables([temperatureDto(42.0)], state, baseline);

    expect(merged).toBeNull();
    expect(state.variables.temperature.rows[0].value).toBe(99.0);
  });

  it("ignores uiIcon but adopts newly created variables", () => {
    const state = { variables: { temperature: record(21.5) } };
    const baseline = { variables: { temperature: record(21.5) } };

    const merged = applyRemoteVariables(
      [
        temperatureDto(21.5),
        {
          ...temperatureDto(0),
          name: "uiIcon",
          value: record(0),
        },
        {
          ...temperatureDto(0),
          name: "pressure",
          value: record(3.0),
        },
      ],
      state,
      baseline,
    );

    expect(merged?.state.variables.pressure.rows[0].value).toBe(3.0);
    expect(merged?.baseline.variables.pressure.rows[0].value).toBe(3.0);
    expect(merged?.state.variables.uiIcon).toBeUndefined();
    expect(merged?.state.variableHistory?.pressure).toEqual(
      expect.objectContaining({ historyEnabled: false }),
    );
    expect(merged?.baseline.variableHistory?.pressure).toEqual(
      expect.objectContaining({ historyEnabled: false }),
    );
  });
});
