import type { PlatformBindingEntry } from "./platformBindings";

/** Historian CEL functions for analytics expressions (BL-211). */
export const ANALYTICS_CEL_BINDING_ENTRIES: PlatformBindingEntry[] = [
  {
    id: "histAvg",
    name: "hist.avg",
    snippet: "hist.avg('root.platform.devices.demo', 'temperature', '5m')",
    category: "aggregate",
    stringQuoteStyle: "single",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "5m" },
    ],
  },
  {
    id: "histMin",
    name: "hist.min",
    snippet: "hist.min('root.platform.devices.demo', 'temperature', '1h')",
    category: "aggregate",
    stringQuoteStyle: "single",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "1h" },
    ],
  },
  {
    id: "histMax",
    name: "hist.max",
    snippet: "hist.max('root.platform.devices.demo', 'temperature', '1h')",
    category: "aggregate",
    stringQuoteStyle: "single",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "1h" },
    ],
  },
  {
    id: "histLast",
    name: "hist.last",
    snippet: "hist.last('root.platform.devices.demo', 'temperature', '5m')",
    category: "aggregate",
    stringQuoteStyle: "single",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "5m" },
    ],
  },
  {
    id: "histSum",
    name: "hist.sum",
    snippet: "hist.sum('root.platform.devices.demo', 'temperature', '1h')",
    category: "aggregate",
    stringQuoteStyle: "single",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "1h" },
    ],
  },
  {
    id: "histLive",
    name: "hist.live",
    snippet: "hist.live('root.platform.devices.demo', 'temperature')",
    category: "signal",
    stringQuoteStyle: "single",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
    ],
  },
];
