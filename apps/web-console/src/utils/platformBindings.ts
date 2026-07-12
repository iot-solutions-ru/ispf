/**
 * Catalog of built-in platform binding functions.
 * Keep in sync with {@code PlatformBindingRegistry} (ispf-expression).
 */
export type PlatformBindingCategory = "signal" | "stateful" | "cross" | "function" | "aggregate";

export type BindingParamKind = "var" | "path" | "number" | "string" | "unit" | "func";

export interface BindingParamDef {
  key: string;
  kind: BindingParamKind;
  /** i18n key under inspector namespace */
  labelKey: string;
  default?: string;
  optional?: boolean;
}

export interface PlatformBindingEntry {
  id: string;
  name: string;
  snippet: string;
  stateful?: boolean;
  category: PlatformBindingCategory;
  params: BindingParamDef[];
  /** Quote style for string params (default double). Analytics helpers use single quotes. */
  stringQuoteStyle?: "single" | "double";
}

export interface BindingBuilderContext {
  objectPath?: string;
  variableNames?: string[];
  functionNames?: string[];
}

function quoteString(value: string, style: "single" | "double" = "double"): string {
  if (style === "single") {
    return `'${value.replace(/\\/g, "\\\\").replace(/'/g, "\\'")}'`;
  }
  return `"${value.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

export function defaultParamValues(
  entry: PlatformBindingEntry,
  ctx: BindingBuilderContext = {}
): Record<string, string> {
  const firstVar = ctx.variableNames?.[0] ?? "sourceVar";
  const firstFn = ctx.functionNames?.[0] ?? "myFunc";
  const values: Record<string, string> = {};

  for (const param of entry.params) {
    switch (param.kind) {
      case "var":
        values[param.key] = firstVar;
        break;
      case "func":
        values[param.key] = firstFn;
        break;
      case "path":
        values[param.key] = ctx.objectPath ?? "root.platform.devices.demo";
        break;
      case "number":
      case "string":
      case "unit":
        values[param.key] =
          param.key === "source" && ctx.variableNames?.[0]
            ? ctx.variableNames[0]
            : (param.default ?? "");
        break;
    }
  }
  return values;
}

export function buildPlatformBindingExpression(
  entry: PlatformBindingEntry,
  values: Record<string, string>
): string {
  const path = values.path?.trim();
  const remoteVar = values.remoteVar?.trim();
  if (entry.id === "readRef" && path && remoteVar) {
    return `read(${quoteString(`${path}/${remoteVar}`)})`;
  }
  const fn = values.function?.trim();
  const input = values.input?.trim();
  if (entry.id === "callRef" && fn) {
    return input ? `call(@/fn/${fn}, @/${input})` : `call(@/fn/${fn})`;
  }
  if (entry.id === "callRemoteRef" && path && fn) {
    const fnRef = `${path}/fn/${fn}`;
    return input ? `call(${fnRef}, @/${input})` : `call(${fnRef})`;
  }
  if (entry.id === "avgHistorian") {
    const source = values.source?.trim() || "sourceVar";
    const window = values.windowBucket?.trim() || "5m";
    return `avg(@/${source}, ${window})`;
  }

  const parts: string[] = [];

  for (const param of entry.params) {
    const raw = values[param.key]?.trim() ?? "";
    if (!raw) {
      if (param.optional) {
        continue;
      }
      return entry.snippet;
    }
    switch (param.kind) {
      case "var":
      case "func":
      case "number":
      case "unit":
        parts.push(raw);
        break;
      case "path":
      case "string":
        parts.push(quoteString(raw, entry.stringQuoteStyle ?? "double"));
        break;
    }
  }

  return `${entry.name}(${parts.join(", ")})`;
}

export const PLATFORM_BINDING_ENTRIES: PlatformBindingEntry[] = [
  {
    id: "selectField",
    name: "selectField",
    snippet: "selectField(@/sourceVar)",
    category: "signal",
    params: [{ key: "source", kind: "var", labelKey: "platformBindings.param.source" }],
  },
  {
    id: "scale",
    name: "scale",
    snippet: "scale(@/sourceVar, 0, 100, 0, 1)",
    category: "signal",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "inMin", kind: "number", labelKey: "platformBindings.param.inMin", default: "0" },
      { key: "inMax", kind: "number", labelKey: "platformBindings.param.inMax", default: "100" },
      { key: "outMin", kind: "number", labelKey: "platformBindings.param.outMin", default: "0" },
      { key: "outMax", kind: "number", labelKey: "platformBindings.param.outMax", default: "1" },
    ],
  },
  {
    id: "clamp",
    name: "clamp",
    snippet: "clamp(@/sourceVar, 0, 100)",
    category: "signal",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "min", kind: "number", labelKey: "platformBindings.param.min", default: "0" },
      { key: "max", kind: "number", labelKey: "platformBindings.param.max", default: "100" },
    ],
  },
  {
    id: "format",
    name: "format",
    snippet: 'format("%.1f", @/sourceVar)',
    category: "signal",
    params: [
      { key: "pattern", kind: "string", labelKey: "platformBindings.param.pattern", default: "%.1f" },
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
    ],
  },
  {
    id: "delta",
    name: "delta",
    snippet: "delta(@/sourceVar)",
    stateful: true,
    category: "stateful",
    params: [{ key: "source", kind: "var", labelKey: "platformBindings.param.source" }],
  },
  {
    id: "rate",
    name: "rate",
    snippet: "rate(@/sourceVar)",
    stateful: true,
    category: "stateful",
    params: [{ key: "source", kind: "var", labelKey: "platformBindings.param.source" }],
  },
  {
    id: "counterRate",
    name: "counterRate",
    snippet: "counterRate(@/ifInOctets)",
    stateful: true,
    category: "stateful",
    params: [{ key: "source", kind: "var", labelKey: "platformBindings.param.source" }],
  },
  {
    id: "counterDelta",
    name: "counterDelta",
    snippet: "counterDelta(@/ifInOctets)",
    stateful: true,
    category: "stateful",
    params: [{ key: "source", kind: "var", labelKey: "platformBindings.param.source" }],
  },
  {
    id: "movingAvg",
    name: "movingAvg",
    snippet: "movingAvg(@/sourceVar, 60)",
    stateful: true,
    category: "stateful",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "windowSec", kind: "number", labelKey: "platformBindings.param.windowSec", default: "60" },
    ],
  },
  {
    id: "movingMin",
    name: "movingMin",
    snippet: "movingMin(@/sourceVar, 30)",
    stateful: true,
    category: "stateful",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "windowSec", kind: "number", labelKey: "platformBindings.param.windowSec", default: "30" },
    ],
  },
  {
    id: "movingMax",
    name: "movingMax",
    snippet: "movingMax(@/sourceVar, 30)",
    stateful: true,
    category: "stateful",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "windowSec", kind: "number", labelKey: "platformBindings.param.windowSec", default: "30" },
    ],
  },
  {
    id: "deadband",
    name: "deadband",
    snippet: "deadband(@/sourceVar, 1.0)",
    stateful: true,
    category: "stateful",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "band", kind: "number", labelKey: "platformBindings.param.band", default: "1.0" },
    ],
  },
  {
    id: "hysteresis",
    name: "hysteresis",
    snippet: "hysteresis(@/sourceVar, 80, 70)",
    stateful: true,
    category: "stateful",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "onThreshold", kind: "number", labelKey: "platformBindings.param.onThreshold", default: "80" },
      { key: "offThreshold", kind: "number", labelKey: "platformBindings.param.offThreshold", default: "70" },
    ],
  },
  {
    id: "unitConvert",
    name: "unitConvert",
    snippet: "unitConvert(@/temperature, C, F)",
    category: "signal",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "fromUnit", kind: "unit", labelKey: "platformBindings.param.fromUnit", default: "C" },
      { key: "toUnit", kind: "unit", labelKey: "platformBindings.param.toUnit", default: "F" },
    ],
  },
  {
    id: "readRef",
    name: "read",
    snippet: 'read("root.platform.devices.demo/temperature")',
    category: "cross",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "remoteVar", kind: "var", labelKey: "platformBindings.param.remoteVar" },
    ],
  },
  {
    id: "callRef",
    name: "call",
    snippet: "call(@/fn/myFunc, @/inputVar)",
    category: "function",
    params: [
      { key: "function", kind: "func", labelKey: "platformBindings.param.function" },
      { key: "input", kind: "var", labelKey: "platformBindings.param.input", optional: true },
    ],
  },
  {
    id: "callRemoteRef",
    name: "call",
    snippet: 'call(root.platform.devices.remote/fn/myFunc, @/inputVar)',
    category: "function",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "function", kind: "func", labelKey: "platformBindings.param.function" },
      { key: "input", kind: "var", labelKey: "platformBindings.param.input", optional: true },
    ],
  },
  {
    id: "sumRecordField",
    name: "sumRecordField",
    snippet: 'sumRecordField(@/tableVar, "amount")',
    category: "aggregate",
    params: [
      { key: "table", kind: "var", labelKey: "platformBindings.param.table" },
      { key: "field", kind: "string", labelKey: "platformBindings.param.field", default: "amount" },
    ],
  },
  {
    id: "avgHistorian",
    name: "avg",
    snippet: "avg(@/sourceVar, 5m)",
    category: "aggregate",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "5m" },
    ],
  },
  {
    id: "rateOfChange",
    name: "rateOfChange",
    snippet: "rateOfChange(@/sourceVar, 1h)",
    category: "aggregate",
    params: [
      { key: "source", kind: "var", labelKey: "platformBindings.param.source" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "1h" },
    ],
  },
];

export const PLATFORM_BINDING_NAMES = PLATFORM_BINDING_ENTRIES.map((entry) => entry.name);

export function filterPlatformBindings(query: string, entries: PlatformBindingEntry[] = PLATFORM_BINDING_ENTRIES): PlatformBindingEntry[] {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return entries;
  }
  return entries.filter(
    (entry) =>
      entry.id.toLowerCase().includes(normalized) ||
      entry.name.toLowerCase().includes(normalized) ||
      entry.snippet.toLowerCase().includes(normalized) ||
      entry.category.toLowerCase().includes(normalized)
  );
}

export function suggestPlatformBindingPrefix(
  input: string,
  entries: PlatformBindingEntry[] = PLATFORM_BINDING_ENTRIES
): PlatformBindingEntry[] {
  const trimmed = input.trim();
  const paren = trimmed.indexOf("(");
  const head = (paren >= 0 ? trimmed.slice(0, paren) : trimmed).trim().toLowerCase();
  if (!head) {
    return entries;
  }
  return entries.filter(
    (entry) =>
      entry.name.toLowerCase().startsWith(head) || entry.name.toLowerCase().includes(head)
  );
}
