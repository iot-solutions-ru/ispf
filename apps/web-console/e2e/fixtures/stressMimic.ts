/** Large mimic document for runtime FPS profiling (S21-01 / BL-152). */
const LAYER = "layer-default";
const BIND_PATH = "root.platform.devices.lab-sensor";

export interface StressMimicOptions {
  elementCount?: number;
  /** When true, bind a subset of elements for live WS FPS path. */
  withBindings?: boolean;
  boundEvery?: number;
}

/**
 * Stress mimic. Optional live bindings for WebSocket VARIABLE_UPDATED FPS path.
 */
export function buildStressMimicDocument(
  elementCountOrOptions: number | StressMimicOptions = 500,
  maybeBoundEvery?: number
) {
  const options: StressMimicOptions =
    typeof elementCountOrOptions === "number"
      ? { elementCount: elementCountOrOptions, boundEvery: maybeBoundEvery }
      : elementCountOrOptions;
  const elementCount = options.elementCount ?? 500;
  const withBindings = options.withBindings === true;
  const boundEvery = options.boundEvery ?? 25;
  const cols = 20;
  const elements = Array.from({ length: elementCount }, (_, index) => {
    const bound = withBindings && index % boundEvery === 0;
    return {
      id: `stress-el-${index}`,
      layerId: LAYER,
      symbolId: "custom.svg",
      x: 40 + (index % cols) * 64,
      y: 40 + Math.floor(index / cols) * 48,
      width: 56,
      height: 40,
      rotation: 0,
      props: {
        width: 56,
        height: 40,
        viewBox: "0 0 56 40",
        svg: '<rect width="56" height="40" fill="#3fb950" rx="4"/>',
      },
      bindings: bound
        ? {
            value: {
              objectPath: BIND_PATH,
              variableName: "temperature",
              valueField: "value",
            },
          }
        : {},
      formatRules: bound
        ? [
            {
              id: `fr-${index}`,
              bindingKey: "value",
              operator: ">",
              value: 0,
              style: { fill: "#58a6ff" },
            },
          ]
        : [],
    };
  });

  return {
    version: 2,
    width: 1400,
    height: 900,
    background: "#0d1117",
    grid: { visible: false, snap: false, size: 20 },
    layers: [{ id: LAYER, name: "Main", visible: true }],
    elements,
    connections: [],
    customSymbols: [],
  };
}

export const STRESS_MIMIC_BIND_PATH = BIND_PATH;
