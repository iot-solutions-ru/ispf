import { ANALYTICS_CEL_BINDING_ENTRIES } from "./analyticsCelBindings";
import { PLATFORM_BINDING_ENTRIES, type PlatformBindingEntry } from "./platformBindings";

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

export const HISTORIAN_EXPRESSION_ENTRIES: PlatformBindingEntry[] = [
  ...PLATFORM_BINDING_ENTRIES.filter((entry) => HISTORIAN_BUILTIN_IDS.has(entry.id)),
  OEE_ENTRY,
  ...ANALYTICS_CEL_BINDING_ENTRIES,
];
