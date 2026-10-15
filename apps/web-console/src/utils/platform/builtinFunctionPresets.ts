import type { DataSchema, FunctionDescriptor } from "../../types";
import { cloneSchema } from "../schema/dataSchema";

export type BuiltinFunctionPresetKind = "handler" | "pulse";

export type BuiltinFunctionPresetGroup =
  | "virtualLab"
  | "alarm"
  | "dataSource"
  | "mqtt"
  | "pulse";

export interface BuiltinFunctionPreset {
  id: string;
  kind: BuiltinFunctionPresetKind;
  group: BuiltinFunctionPresetGroup;
  functionName: string;
  /** i18n keys: descriptor.builtinPreset.{id}.label / .description */
  inputSchema: DataSchema;
  outputSchema: DataSchema;
  sourceType: string | null;
  sourceBody: string | null;
  templateIds?: string[];
}

const voidInput = (): DataSchema => ({ name: "voidInput", fields: [] });

const functionResult = (): DataSchema => ({
  name: "functionResult",
  fields: [
    { name: "success", type: "BOOLEAN", description: "", nullable: true },
    { name: "message", type: "STRING", description: "", nullable: true },
  ],
});

const eventInput = (): DataSchema => ({
  name: "eventInput",
  fields: [
    { name: "int", type: "INTEGER", description: "", nullable: true },
    { name: "string", type: "STRING", description: "", nullable: true },
  ],
});

const calculateInput = (): DataSchema => ({
  name: "calculateInput",
  fields: [
    { name: "inputA", type: "DOUBLE", description: "", nullable: true },
    { name: "inputB", type: "DOUBLE", description: "", nullable: true },
  ],
});

const calculateOutput = (): DataSchema => ({
  name: "calculateOutput",
  fields: [{ name: "result", type: "DOUBLE", description: "", nullable: true }],
});

const executeQueryInput = (): DataSchema => ({
  name: "executeQueryInput",
  fields: [
    { name: "query", type: "STRING", description: "", nullable: true },
    { name: "paramsJson", type: "STRING", description: "", nullable: true },
    { name: "maxRows", type: "INTEGER", description: "", nullable: true },
  ],
});

const executeQueryOutput = (): DataSchema => ({
  name: "executeQueryOutput",
  fields: [
    { name: "kind", type: "STRING", description: "", nullable: true },
    { name: "rowCount", type: "INTEGER", description: "", nullable: true },
    { name: "updateCount", type: "INTEGER", description: "", nullable: true },
    { name: "rowsJson", type: "STRING", description: "", nullable: true },
  ],
});

const mqttIngress = (): DataSchema => ({
  name: "mqttIngress",
  fields: [
    { name: "topic", type: "STRING", description: "", nullable: true },
    { name: "raw", type: "STRING", description: "", nullable: true },
  ],
});

const dispatchStatus = (): DataSchema => ({
  name: "dispatchStatus",
  fields: [
    { name: "ok", type: "BOOLEAN", description: "", nullable: true },
    { name: "message", type: "STRING", description: "", nullable: true },
    { name: "routedPath", type: "STRING", description: "", nullable: true },
  ],
});

/** Platform handler / pulse presets (static catalog — keep in sync with server handlers). */
export const BUILTIN_FUNCTION_PRESETS: readonly BuiltinFunctionPreset[] = [
  {
    id: "calculate",
    kind: "handler",
    group: "virtualLab",
    functionName: "calculate",
    templateIds: ["virtual-lab-v1", "virtual-unified-v1"],
    inputSchema: calculateInput(),
    outputSchema: calculateOutput(),
    sourceType: null,
    sourceBody: null,
  },
  {
    id: "fireEvent1",
    kind: "handler",
    group: "virtualLab",
    functionName: "fireEvent1",
    templateIds: ["virtual-lab-v1"],
    inputSchema: eventInput(),
    outputSchema: functionResult(),
    sourceType: null,
    sourceBody: null,
  },
  {
    id: "fireEvent2",
    kind: "handler",
    group: "virtualLab",
    functionName: "fireEvent2",
    templateIds: ["virtual-lab-v1"],
    inputSchema: eventInput(),
    outputSchema: functionResult(),
    sourceType: null,
    sourceBody: null,
  },
  {
    id: "appendTableRow",
    kind: "handler",
    group: "virtualLab",
    functionName: "appendTableRow",
    templateIds: ["virtual-lab-v1"],
    inputSchema: eventInput(),
    outputSchema: functionResult(),
    sourceType: null,
    sourceBody: null,
  },
  {
    id: "acknowledgeAlarm",
    kind: "handler",
    group: "alarm",
    functionName: "acknowledgeAlarm",
    templateIds: ["mqtt-sensor-v1"],
    inputSchema: voidInput(),
    outputSchema: functionResult(),
    sourceType: null,
    sourceBody: null,
  },
  {
    id: "executeQuery",
    kind: "handler",
    group: "dataSource",
    functionName: "executeQuery",
    inputSchema: executeQueryInput(),
    outputSchema: executeQueryOutput(),
    sourceType: null,
    sourceBody: null,
  },
  {
    id: "dispatchTelemetry",
    kind: "handler",
    group: "mqtt",
    functionName: "dispatchTelemetry",
    templateIds: ["mqtt-gateway-v1"],
    inputSchema: mqttIngress(),
    outputSchema: dispatchStatus(),
    sourceType: null,
    sourceBody: null,
  },
  {
    id: "pulseCmdStart",
    kind: "pulse",
    group: "pulse",
    functionName: "cmd_start",
    inputSchema: voidInput(),
    outputSchema: functionResult(),
    sourceType: "pulse",
    sourceBody: JSON.stringify({ variable: "cmdStart" }),
  },
];

export function findBuiltinPresetById(id: string): BuiltinFunctionPreset | undefined {
  return BUILTIN_FUNCTION_PRESETS.find((preset) => preset.id === id);
}

export function resolveBuiltinPresetId(fn: Pick<
  FunctionDescriptor,
  "name" | "sourceType" | "sourceBody"
>): string {
  const body = fn.sourceBody?.trim() ?? "";
  const type = fn.sourceType?.trim() ?? "";
  for (const preset of BUILTIN_FUNCTION_PRESETS) {
    if (preset.functionName !== fn.name) continue;
    if (preset.kind === "pulse") {
      if (type.toLowerCase() === "pulse" && body === (preset.sourceBody ?? "")) {
        return preset.id;
      }
      continue;
    }
    if (!type && !body) {
      return preset.id;
    }
  }
  return "";
}

export function applyBuiltinPreset(preset: BuiltinFunctionPreset): {
  name: string;
  description: string;
  inputSchema: DataSchema;
  outputSchema: DataSchema;
  sourceType: string;
  sourceBody: string;
} {
  return {
    name: preset.functionName,
    description: "", // filled from i18n in dialog
    inputSchema: cloneSchema(preset.inputSchema),
    outputSchema: cloneSchema(preset.outputSchema),
    sourceType: preset.sourceType ?? "",
    sourceBody: preset.sourceBody ?? "",
  };
}

export function presetUsesBuiltinSource(sourceType: string): boolean {
  return sourceType === "" || sourceType === "pulse";
}

export const EXECUTE_QUERY_FUNCTION_NAME = "executeQuery";

/** Handler (empty sourceType) named executeQuery — uses descriptor dataSourcePath like script SQL steps. */
export function isExecuteQueryHandlerFunction(
  functionName: string,
  sourceType: string | null | undefined
): boolean {
  if ((sourceType?.trim() ?? "") !== "") {
    return false;
  }
  return functionName.trim() === EXECUTE_QUERY_FUNCTION_NAME;
}

export function functionHostIsDataSourceObject(objectPath: string): boolean {
  return objectPath.includes(".data-sources.");
}

export function builtinPresetKindForSourceType(sourceType: string): BuiltinFunctionPresetKind {
  return sourceType === "pulse" ? "pulse" : "handler";
}
