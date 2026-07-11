/** Large mimic document for runtime FPS profiling (S21-01). */
const LAYER = "layer-default";

export function buildStressMimicDocument(elementCount = 500) {
  const cols = 20;
  const elements = Array.from({ length: elementCount }, (_, index) => ({
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
    bindings: {},
    formatRules: [],
  }));

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
