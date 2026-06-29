import { mimicDocumentToJson } from "../document";
import type { MimicConnection, ScadaMimicDocument } from "../../types/scadaMimic";

const P = {
  gpu1: "root.platform.devices.mini-tec-plant.gpu-01",
  gpu2: "root.platform.devices.mini-tec-plant.gpu-02",
  gpu3: "root.platform.devices.mini-tec-plant.gpu-03",
  grpb: "root.platform.devices.mini-tec-plant.grpb",
  rumb: "root.platform.devices.mini-tec-plant.rumb-10kv",
  dgu: "root.platform.devices.mini-tec-plant.dgu",
  load: "root.platform.devices.mini-tec-plant.load-module",
  hub: "root.platform.devices.mini-tec-plant.station-hub",
} as const;

const LAYER = "layer-default";

function bind(path: string, variableName: string, transform?: "bool" | "number") {
  return { objectPath: path, variableName, valueField: "value", transform };
}

function wire(
  id: string,
  from: [string, string],
  to: [string, string],
  points: { x: number; y: number }[],
  style?: MimicConnection["style"]
): MimicConnection {
  return {
    id,
    layerId: LAYER,
    from: { elementId: from[0], port: from[1] },
    to: { elementId: to[0], port: to[1] },
    points,
    style,
  };
}

/** Single-line mini-TEC diagram — layout aligned with {@link MiniTecSldWidgetView}. */
export const MINI_TEC_SLD_DOCUMENT: ScadaMimicDocument = {
  version: 1,
  width: 1200,
  height: 400,
  background: "var(--bg)",
  grid: { size: 20, snap: false, visible: false },
  layers: [{ id: LAYER, name: "Main", visible: true }],
  elements: [
    {
      id: "island-banner",
      symbolId: "alarm.banner",
      layerId: LAYER,
      x: 420,
      y: 8,
      props: { width: 360, height: 22, text: "Островной режим / Island mode" },
      bindings: { active: bind(P.hub, "islandMode", "bool") },
    },
    {
      id: "label-gas",
      symbolId: "label",
      layerId: LAYER,
      x: 28,
      y: 116,
      props: { text: "Gas", width: 40, height: 16 },
      bindings: {},
    },
    {
      id: "grpb-flow",
      symbolId: "data-block",
      layerId: LAYER,
      x: 120,
      y: 78,
      props: { width: 104, height: 88, line1: "GRPB", line2: "Gas" },
      bindings: { line3: bind(P.grpb, "gasFlowRate", "number") },
    },
    {
      id: "grpb-alarm",
      symbolId: "sensor.indicator",
      layerId: LAYER,
      x: 168,
      y: 158,
      bindings: { state: bind(P.grpb, "fireAlarm", "bool") },
      formatRules: [
        {
          id: "alarm-red",
          bindingKey: "state",
          operator: "==",
          value: true,
          style: { fill: "#f85149" },
        },
      ],
    },
    {
      id: "busbar-main",
      symbolId: "busbar.horizontal",
      layerId: LAYER,
      x: 248,
      y: 114,
      props: { width: 680, height: 12 },
      bindings: { energized: bind(P.hub, "islandMode", "bool") },
    },
    {
      id: "hub-freq",
      symbolId: "value-badge",
      layerId: LAYER,
      x: 480,
      y: 88,
      props: { unit: " Hz", decimals: 2 },
      bindings: { value: bind(P.hub, "gridFrequencyHz", "number") },
    },
    {
      id: "hub-power",
      symbolId: "value-badge",
      layerId: LAYER,
      x: 620,
      y: 88,
      props: { unit: " kW", decimals: 0 },
      bindings: { value: bind(P.hub, "totalGenPowerKw", "number") },
    },
    {
      id: "gen-gpu1",
      symbolId: "gen.block",
      layerId: LAYER,
      x: 268,
      y: 24,
      props: { label: "GPU-1", ratedKw: 1480 },
      bindings: {
        running: bind(P.gpu1, "running", "bool"),
        power: bind(P.gpu1, "activePowerKw", "number"),
      },
    },
    {
      id: "gen-gpu2",
      symbolId: "gen.block",
      layerId: LAYER,
      x: 418,
      y: 24,
      props: { label: "GPU-2", ratedKw: 1480 },
      bindings: {
        running: bind(P.gpu2, "running", "bool"),
        power: bind(P.gpu2, "activePowerKw", "number"),
      },
    },
    {
      id: "gen-gpu3",
      symbolId: "gen.block",
      layerId: LAYER,
      x: 568,
      y: 24,
      props: { label: "GPU-3", ratedKw: 1480 },
      bindings: {
        running: bind(P.gpu3, "running", "bool"),
        power: bind(P.gpu3, "activePowerKw", "number"),
      },
    },
    {
      id: "dgu-block",
      symbolId: "gen.block",
      layerId: LAYER,
      x: 488,
      y: 44,
      props: { label: "DGU", ratedKw: 500 },
      bindings: {
        running: bind(P.dgu, "running", "bool"),
        power: bind(P.dgu, "activePowerKw", "number"),
      },
    },
    {
      id: "breaker-rumb",
      symbolId: "breaker",
      layerId: LAYER,
      x: 768,
      y: 90,
      bindings: { closed: bind(P.rumb, "breakerClosed", "bool") },
      actions: [
        {
          id: "toggle-breaker",
          type: "invokeFunction",
          objectPath: P.rumb,
          functionName: "breaker_operate",
        },
      ],
    },
    {
      id: "rumb-block",
      symbolId: "data-block",
      layerId: LAYER,
      x: 792,
      y: 80,
      props: { width: 108, height: 84, line1: "RUMB", line2: "10/0.4 kV", line3: "Breaker" },
      bindings: { line4: bind(P.rumb, "breakerClosed", "bool") },
    },
    {
      id: "xfmr-rumb",
      symbolId: "transformer.two-winding",
      layerId: LAYER,
      x: 782,
      y: 122,
      bindings: { loaded: bind(P.rumb, "breakerClosed", "bool") },
    },
    {
      id: "load-block",
      symbolId: "load.block",
      layerId: LAYER,
      x: 944,
      y: 68,
      props: { width: 128, height: 104, label: "Load" },
      bindings: { power: bind(P.load, "activePowerKw", "number") },
    },
    {
      id: "legend-panel",
      symbolId: "data-block",
      layerId: LAYER,
      x: 24,
      y: 300,
      props: {
        width: 1152,
        height: 84,
        line1: "Legend",
        line2: "● Generation  ● Gas  ● DGU reserve  — Busbar  □ Load",
        line3: "Rated values are nameplate; live values update from plant model",
      },
      bindings: {},
    },
  ],
  connections: [
    wire(
      "conn-gas-feed",
      ["grpb-flow", "w"],
      ["grpb-flow", "w"],
      [
        { x: 52, y: 122 },
        { x: 118, y: 122 },
      ],
      { stroke: "#f0883e", strokeWidth: 3 }
    ),
    wire(
      "conn-grpb-bus",
      ["grpb-flow", "e"],
      ["busbar-main", "w"],
      [
        { x: 224, y: 122 },
        { x: 248, y: 122 },
      ],
      { stroke: "#58a6ff", strokeWidth: 3 }
    ),
    wire(
      "conn-gen1-bus",
      ["gen-gpu1", "s"],
      ["busbar-main", "w"],
      [
        { x: 324, y: 136 },
        { x: 324, y: 120 },
      ],
      { stroke: "#58a6ff", strokeWidth: 3 }
    ),
    wire(
      "conn-gen2-bus",
      ["gen-gpu2", "s"],
      ["busbar-main", "w"],
      [
        { x: 474, y: 136 },
        { x: 474, y: 120 },
      ],
      { stroke: "#58a6ff", strokeWidth: 3 }
    ),
    wire(
      "conn-gen3-bus",
      ["gen-gpu3", "s"],
      ["busbar-main", "w"],
      [
        { x: 624, y: 136 },
        { x: 624, y: 120 },
      ],
      { stroke: "#58a6ff", strokeWidth: 3 }
    ),
    wire(
      "conn-dgu-bus",
      ["dgu-block", "s"],
      ["busbar-main", "w"],
      [
        { x: 544, y: 156 },
        { x: 544, y: 120 },
      ],
      { stroke: "#58a6ff", strokeWidth: 3 }
    ),
    wire(
      "conn-bus-breaker",
      ["busbar-main", "e"],
      ["breaker-rumb", "n"],
      [
        { x: 748, y: 120 },
        { x: 768, y: 120 },
        { x: 780, y: 90 },
      ],
      { stroke: "#58a6ff", strokeWidth: 3 }
    ),
    wire(
      "conn-breaker-xfmr",
      ["breaker-rumb", "s"],
      ["xfmr-rumb", "w"],
      [
        { x: 780, y: 142 },
        { x: 782, y: 142 },
      ],
      { stroke: "#58a6ff", strokeWidth: 3 }
    ),
    wire(
      "conn-feed-load",
      ["xfmr-rumb", "e"],
      ["load-block", "n"],
      [
        { x: 838, y: 142 },
        { x: 920, y: 142 },
        { x: 920, y: 120 },
        { x: 944, y: 120 },
        { x: 1008, y: 120 },
        { x: 1008, y: 68 },
      ],
      { stroke: "#58a6ff", strokeWidth: 3 }
    ),
  ],
};

export const MINI_TEC_SLD_DOCUMENT_JSON = mimicDocumentToJson(MINI_TEC_SLD_DOCUMENT);
