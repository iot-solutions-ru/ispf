import { describe, expect, it } from "vitest";
import type { FunctionDescriptor } from "../../types";
import { functionCopyDescriptor, suggestedFunctionCopyName } from "./copyFunctionName";

const source: FunctionDescriptor = {
  name: "latchAlarm",
  description: "Latches the alarm",
  inputSchema: { name: "input", fields: [{ name: "reason", type: "STRING" }] },
  outputSchema: { name: "output", fields: [] },
  sourceType: "script",
  sourceBody: "  return input;  ",
  dataSourcePath: " root.platform.devices ",
  version: " 1 ",
  invokeRoles: ["operator"],
};

describe("suggestedFunctionCopyName", () => {
  it("appends Copy when the result is a code", () => {
    expect(suggestedFunctionCopyName("latchAlarm")).toBe("latchAlarmCopy");
  });

  it("leaves the field empty when the source name is not a code", () => {
    expect(suggestedFunctionCopyName("alarm-latched")).toBe("");
  });
});

describe("functionCopyDescriptor", () => {
  it("keeps schemas and body and applies the new name", () => {
    const copy = functionCopyDescriptor(source, " latchAlarmCopy ");
    expect(copy.name).toBe("latchAlarmCopy");
    expect(copy.description).toBe(source.description);
    expect(copy.inputSchema).toEqual(source.inputSchema);
    expect(copy.outputSchema).toEqual(source.outputSchema);
    expect(copy.sourceType).toBe("script");
    expect(copy.sourceBody).toBe("return input;");
    expect(copy.dataSourcePath).toBe("root.platform.devices");
    expect(copy.version).toBe("1");
    expect(copy.invokeRoles).toEqual(["operator"]);
  });
});
