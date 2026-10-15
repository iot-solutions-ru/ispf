import { describe, expect, it } from "vitest";
import {
  BUILTIN_FUNCTION_PRESETS,
  applyBuiltinPreset,
  findBuiltinPresetById,
  resolveBuiltinPresetId,
  builtinPresetKindForSourceType,
  isExecuteQueryHandlerFunction,
} from "./builtinFunctionPresets";

describe("builtinFunctionPresets", () => {
  it("includes core virtual lab and alarm handlers", () => {
    const ids = BUILTIN_FUNCTION_PRESETS.map((p) => p.id);
    expect(ids).toContain("calculate");
    expect(ids).toContain("acknowledgeAlarm");
    expect(ids).toContain("pulseCmdStart");
  });

  it("resolves handler preset by function name", () => {
    expect(
      resolveBuiltinPresetId({
        name: "calculate",
        sourceType: null,
        sourceBody: null,
      }),
    ).toBe("calculate");
  });

  it("detects executeQuery handler by name", () => {
    expect(isExecuteQueryHandlerFunction("executeQuery", null)).toBe(true);
    expect(isExecuteQueryHandlerFunction("executeQuery", "script")).toBe(false);
  });

  it("maps source type to preset kind", () => {
    expect(builtinPresetKindForSourceType("")).toBe("handler");
    expect(builtinPresetKindForSourceType("pulse")).toBe("pulse");
  });

  it("applies preset clones schemas", () => {
    const preset = findBuiltinPresetById("executeQuery");
    expect(preset).toBeDefined();
    const applied = applyBuiltinPreset(preset!);
    expect(applied.name).toBe("executeQuery");
    expect(applied.inputSchema.fields.map((f) => f.name)).toEqual(["query", "paramsJson", "maxRows"]);
    expect(applied.sourceType).toBe("");
  });
});
