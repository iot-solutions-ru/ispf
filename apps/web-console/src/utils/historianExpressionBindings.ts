import { ANALYTICS_CEL_BINDING_ENTRIES } from "./analyticsCelBindings";
import type { AnalyticsCatalogEntryDto, AnalyticsCatalogParameterDto } from "../api/analyticsCatalog";
import { PLATFORM_BINDING_ENTRIES, type PlatformBindingEntry } from "./platformBindings";
import { matchesAnalyticsCatalogKindFilter } from "./analyticsCatalogKindFilter";

const HISTORIAN_BUILTIN_IDS = new Set(["rollingAvg", "rateOfChange"]);

const OEE_ENTRY: PlatformBindingEntry = {
  id: "oee",
  name: "oee",
  snippet: "oee('availability', 'performance', 'quality')",
  category: "aggregate",
  stringQuoteStyle: "single",
  params: [
    { key: "availability", kind: "string", labelKey: "platformBindings.param.source", default: "availability" },
    { key: "performance", kind: "string", labelKey: "platformBindings.param.source", default: "performance" },
    { key: "quality", kind: "string", labelKey: "platformBindings.param.source", default: "quality" },
  ],
};

export const HISTORIAN_EXPRESSION_FALLBACK_ENTRIES: PlatformBindingEntry[] = [
  ...PLATFORM_BINDING_ENTRIES.filter((entry) => HISTORIAN_BUILTIN_IDS.has(entry.id)),
  OEE_ENTRY,
  ...ANALYTICS_CEL_BINDING_ENTRIES,
];

export const HISTORIAN_EXPRESSION_ENTRIES = HISTORIAN_EXPRESSION_FALLBACK_ENTRIES;

function mapParamKind(param: AnalyticsCatalogParameterDto): PlatformBindingEntry["params"][number]["kind"] {
  const normalized = (param.type ?? "").toLowerCase();
  if (normalized.includes("number") || normalized.includes("int") || normalized.includes("double")) {
    return "number";
  }
  if (normalized.includes("unit")) {
    return "unit";
  }
  if (normalized.includes("path")) {
    return "path";
  }
  return "string";
}

function buildSnippet(entry: AnalyticsCatalogEntryDto): string {
  if (!entry.parameters?.length) {
    return `${entry.id}()`;
  }
  const args = entry.parameters.map((param) => {
    const raw = param.defaultValue?.trim();
    if (raw) {
      return raw;
    }
    return `<${param.name}>`;
  });
  return `${entry.id}(${args.join(", ")})`;
}

export function mapAnalyticsCatalogToBindingEntries(
  catalog: AnalyticsCatalogEntryDto[],
  kind: "historian" | "reactive"
): PlatformBindingEntry[] {
  return catalog
    .filter((entry) => matchesAnalyticsCatalogKindFilter(entry, kind))
    .map((entry) => {
      const params = entry.parameters?.map((param) => ({
        key: param.name,
        kind: mapParamKind(param),
        labelKey: "platformBindings.param.source",
        default: param.defaultValue ?? undefined,
        optional: !param.required,
      })) ?? [];
      return {
        id: entry.id,
        name: entry.id,
        snippet: buildSnippet(entry),
        category: kind === "historian" ? "aggregate" : "function",
        params,
        stringQuoteStyle: "single",
      } satisfies PlatformBindingEntry;
    });
}
