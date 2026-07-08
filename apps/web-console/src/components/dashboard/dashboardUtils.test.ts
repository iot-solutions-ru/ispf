import { describe, expect, it } from "vitest";
import {
  buildFunctionInput,
  formatObjectTableCell,
  matchesNamePattern,
  objectTableValueField,
  parseFunctionInputJson,
  parseWidgetJsonArray,
} from "./dashboardUtils";

describe("parseFunctionInputJson", () => {
  it("omits empty schema so server uses function descriptor fields", () => {
    const input = parseFunctionInputJson('{"jobNo":"PRINT-2026-001"}');
    expect(input.rows[0]?.jobNo).toBe("PRINT-2026-001");
    expect("schema" in input).toBe(false);
  });
});

describe("buildFunctionInput", () => {
  it("includes bound values for hidden fields", () => {
    const input = buildFunctionInput(
      [
        { name: "eventCode", label: "Code", type: "text" },
        { name: "unprocessedId", label: "ID", type: "text", hidden: true },
      ],
      { eventCode: "120", unprocessedId: "560ed061-0000-4000-8000-000000000001" }
    );
    expect(input.rows[0]?.eventCode).toBe("120");
    expect(input.rows[0]?.unprocessedId).toBe("560ed061-0000-4000-8000-000000000001");
  });
});

describe("parseWidgetJsonArray", () => {
  it("parses JSON string columnsJson", () => {
    const cols = parseWidgetJsonArray<{ variable: string }>(
      '[{"variable":"currentRate","label":"Rate"}]'
    );
    expect(cols).toHaveLength(1);
    expect(cols[0]?.variable).toBe("currentRate");
  });

  it("accepts already-parsed array from agent layout", () => {
    const cols = parseWidgetJsonArray<{ variable: string }>([
      { variable: "currencyName", label: "Currency" },
    ]);
    expect(cols).toHaveLength(1);
    expect(cols[0]?.variable).toBe("currencyName");
  });
});

describe("object table helpers", () => {
  it("maps variable field name to value", () => {
    expect(objectTableValueField({ variable: "activePowerKw", field: "activePowerKw" })).toBe(
      "value"
    );
    expect(objectTableValueField({ variable: "activePowerKw", field: "value" })).toBe("value");
  });

  it("matches gpu glob", () => {
    expect(matchesNamePattern("gpu-01", "gpu-*")).toBe(true);
    expect(matchesNamePattern("grpb", "gpu-*")).toBe(false);
  });

  it("formats booleans", () => {
    expect(formatObjectTableCell(true, { trueLabel: "Run", falseLabel: "Stop" })).toBe("Run");
    expect(formatObjectTableCell(false, { trueLabel: "Run", falseLabel: "Stop" })).toBe("Stop");
  });
});
