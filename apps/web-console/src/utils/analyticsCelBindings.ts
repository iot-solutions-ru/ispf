import type { PlatformBindingEntry } from "./platformBindings";

/** Historian CEL functions for analytics expressions (BL-211). */
export const ANALYTICS_CEL_BINDING_ENTRIES: PlatformBindingEntry[] = [
  {
    id: "celAvg",
    name: "avg",
    snippet: "avg(root.platform.devices.demo/temperature, 5m)",
    category: "aggregate",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "5m" },
    ],
  },
  {
    id: "celMin",
    name: "min",
    snippet: "min(root.platform.devices.demo/temperature, 1h)",
    category: "aggregate",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "1h" },
    ],
  },
  {
    id: "celMax",
    name: "max",
    snippet: "max(root.platform.devices.demo/temperature, 1h)",
    category: "aggregate",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "1h" },
    ],
  },
  {
    id: "celLast",
    name: "last",
    snippet: "last(root.platform.devices.demo/temperature, 5m)",
    category: "aggregate",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "5m" },
    ],
  },
  {
    id: "celSum",
    name: "sum",
    snippet: "sum(root.platform.devices.demo/temperature, 1h)",
    category: "aggregate",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
      { key: "windowBucket", kind: "string", labelKey: "platformBindings.param.windowBucket", default: "1h" },
    ],
  },
  {
    id: "celLive",
    name: "live",
    snippet: "live(root.platform.devices.demo/temperature)",
    category: "signal",
    params: [
      { key: "path", kind: "path", labelKey: "platformBindings.param.path" },
      { key: "variable", kind: "string", labelKey: "platformBindings.param.source", default: "temperature" },
    ],
  },
];
