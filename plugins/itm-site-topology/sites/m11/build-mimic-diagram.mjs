import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  COLORS,
  TOPOLOGY_CANVAS,
  extractSvgInner,
  inlineSvgStyles,
  brightenTopologySvg,
  cleanSvgForIspf,
  parseAggParams,
  parseNodeHitAreas,
  buildTopologyHitAreas,
  nodeDevicePath,
  resolveLinkBinding,
  resolveNodeBinding,
} from "./topology-bindings.mjs";

const siteDir = path.dirname(fileURLToPath(import.meta.url));

const defaultSource = path.resolve(
  siteDir,
  "../../../../../YandexDisk/Трасса М11/Interface/main_topology_7_РАБОТАЕТ_07.07.20.svg"
);
const sourcePath = process.env.TOPOLOGY_SVG_SOURCE ?? defaultSource;
const assetsDir = path.join(siteDir, "assets");
const outSvg = path.join(assetsDir, "main_topology.svg");
const outDiagram = path.join(siteDir, "mimic-diagram.json");
const outMeta = path.join(siteDir, "topology-meta.json");

if (!fs.existsSync(sourcePath)) {
  console.error("Source SVG not found:", sourcePath);
  process.exit(1);
}

const rawSvg = fs.readFileSync(sourcePath, "utf8");
const aggParams = parseAggParams(rawSvg);
const nodeParams = aggParams.filter((p) => p.kind === "node");
const linkParams = aggParams.filter((p) => p.kind === "link");
const hitAreas = buildTopologyHitAreas(rawSvg, aggParams);

fs.mkdirSync(assetsDir, { recursive: true });
fs.writeFileSync(outSvg, cleanSvgForIspf(rawSvg), "utf8");

const innerSvg = brightenTopologySvg(inlineSvgStyles(extractSvgInner(rawSvg)));

const bindings = {};
const behaviors = [];
const bindingSchema = [];

for (const param of nodeParams) {
  bindings[param.name] = resolveNodeBinding(param.name);
  bindingSchema.push({ key: param.name, labelKey: `bindings.${param.name}`, type: "boolean" });
  behaviors.push({
    bind: param.name,
    type: "fill",
    target: `#${param.targetId}`,
    trueColor: COLORS.nodeOnline,
    falseColor: COLORS.nodeOffline,
  });
}

for (const param of linkParams) {
  bindings[param.name] = resolveLinkBinding(param.name);
  bindingSchema.push({
    key: param.name,
    labelKey: `bindings.${param.name}`,
    type: "boolean",
    optional: true,
  });
  behaviors.push({
    bind: param.name,
    type: "stroke",
    target: `#${param.targetId}`,
    trueColor: COLORS.linkUp,
    falseColor: COLORS.linkDown,
  });
}

const customSymbols = [
  {
    id: "lib-m11-topology",
    name: "M11 DCN Topology",
    svg: innerSvg,
    width: 1309,
    height: 503,
    viewBox: "0 0 1309 503",
    ports: [],
    bindingSchema,
    behaviors,
    inUserLibrary: false,
  },
  {
    id: "lib-node-hit",
    name: "Node click area",
    svg: '<rect width="100%" height="100%" fill="transparent" stroke="none" opacity="0.001" />',
    width: 32,
    height: 32,
    viewBox: "0 0 32 32",
    ports: [],
    bindingSchema: [],
    behaviors: [],
    inUserLibrary: false,
  },
];

const elements = [
  {
    id: "m11-topology",
    symbolId: "custom:lib-m11-topology",
    layerId: "layer-topology",
    x: 0,
    y: 0,
    props: { width: 1309, height: 503 },
    bindings,
    tooltip: "Топология DCN — М11 (статусы узлов и линков)",
  },
  ...hitAreas.map((area) => ({
    id: `hit-${area.nodeName}`,
    symbolId: "custom:lib-node-hit",
    layerId: "layer-hits",
    x: area.x,
    y: area.y,
    props: {
      width: area.w || 8,
      height: area.h || 8,
      svgHitId: area.id,
      hitKind: area.kind ?? "zone",
    },
    bindings: {},
    actions: [
      {
        id: `sel-${area.nodeName}`,
        type: "setSelection",
        selectionKey: "device",
        objectPath: area.objectPath ?? nodeDevicePath(area.nodeName),
      },
    ],
    tooltip: area.label ?? area.nodeName,
  })),
];

const diagram = {
  version: 2,
  width: 1309,
  height: 503,
  background: TOPOLOGY_CANVAS.diagramBackground,
  grid: { size: 8, snap: false, visible: false },
  layers: [
    { id: "layer-topology", name: "Topology", visible: true, locked: true },
    { id: "layer-hits", name: "Interaction", visible: true, locked: false },
  ],
  elements,
  connections: [],
  customSymbols,
};

fs.writeFileSync(outDiagram, JSON.stringify(diagram));
fs.writeFileSync(
  outMeta,
  JSON.stringify(
    {
      sourcePath,
      nodeCount: nodeParams.length,
      linkCount: linkParams.length,
      hitAreas: hitAreas.length,
      generatedAt: new Date().toISOString(),
    },
    null,
    2
  )
);

console.log(
  `Built mimic-diagram.json: ${nodeParams.length} nodes, ${linkParams.length} links, ${hitAreas.length} click areas`
);
console.log(`Wrote ${outSvg}`);
