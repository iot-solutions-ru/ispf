/**
 * Экранная форма РП (РД-029 §6.4) — резервуарный парк.
 */
import type {
  MimicBinding,
  MimicConnection,
  MimicElement,
  ScadaMimicDocument,
} from "../../../../types/scadaMimic";
import { createMimicId, DEFAULT_LAYER_ID } from "../../../document";
import { pipelineCustomSymbols } from "../symbols";
import { PIPELINE_SCADA_HUB, tankPath } from "../paths";
import { pipelineNavElements } from "./buildShell";

export const RP_CANVAS = { width: 1360, height: 880 };
const BG = "#c0c0c0";
const FONT = "Arial, sans-serif";
const HUB = PIPELINE_SCADA_HUB;
const LAYER_URDO = "layer-urdo";

type TankRow = { num: number; rowColor: string; productTypeA: boolean };

export const TANK_ROWS: TankRow[] = [
  { num: 11, rowColor: "#ffff66", productTypeA: true },
  { num: 12, rowColor: "#ffff66", productTypeA: true },
  { num: 13, rowColor: "#ffff66", productTypeA: true },
  { num: 14, rowColor: "#ffff66", productTypeA: true },
  { num: 15, rowColor: "#ffff66", productTypeA: true },
  { num: 16, rowColor: "#66ccff", productTypeA: true },
  { num: 17, rowColor: "#ffff66", productTypeA: true },
  { num: 18, rowColor: "#66ccff", productTypeA: false },
  { num: 19, rowColor: "#ffff66", productTypeA: false },
  { num: 20, rowColor: "#66ccff", productTypeA: false },
  { num: 21, rowColor: "#66ff66", productTypeA: false },
  { num: 22, rowColor: "#ffff66", productTypeA: false },
  { num: 23, rowColor: "#66ff66", productTypeA: false },
  { num: 24, rowColor: "#66ff66", productTypeA: false },
];

const TANK_POS: Record<number, { x: number; y: number }> = {
  19: { x: 210, y: 62 },
  20: { x: 210, y: 178 },
  21: { x: 210, y: 294 },
  22: { x: 210, y: 410 },
  14: { x: 210, y: 526 },
  15: { x: 430, y: 62 },
  16: { x: 548, y: 62 },
  17: { x: 768, y: 62 },
  18: { x: 768, y: 178 },
  11: { x: 768, y: 294 },
  12: { x: 768, y: 410 },
  13: { x: 768, y: 526 },
  23: { x: 128, y: 526 },
  24: { x: 128, y: 642 },
};

const VALVE_PLACEMENTS: { id: string; valveId: string; x: number; y: number }[] = [
  { id: "v-222", valveId: "222", x: 318, y: 148 },
  { id: "v-214", valveId: "214", x: 318, y: 264 },
  { id: "v-459", valveId: "459", x: 318, y: 380 },
  { id: "v-180", valveId: "180", x: 318, y: 496 },
  { id: "v-301", valveId: "301", x: 500, y: 148 },
  { id: "v-412", valveId: "412", x: 618, y: 148 },
  { id: "v-527", valveId: "527", x: 700, y: 264 },
  { id: "v-638", valveId: "638", x: 700, y: 380 },
  { id: "v-749", valveId: "749", x: 700, y: 496 },
  { id: "v-851", valveId: "851", x: 500, y: 380 },
  { id: "v-963", valveId: "963", x: 380, y: 612 },
  { id: "v-104", valveId: "104", x: 236, y: 612 },
];

const STATIONS = [
  { label: "НПС-1", x: 340, y: 200 },
  { label: "КНС-2", x: 520, y: 260 },
  { label: "НПС-3", x: 620, y: 340 },
  { label: "РП-1", x: 860, y: 200 },
];

const PIPELINE_STATIONS = ["ЛУ-1", "ЛУ-2", "ЛУ-3", "ЛУ-4", "ЛУ-5", "ЛУ-6"];

function tankBinding(n: number, varName: string): MimicBinding {
  return {
    objectPath: tankPath(n),
    variableName: varName,
    valueField: "value",
    transform: varName === "valveOpen" ? "bool" : "number",
  };
}

function el(
  id: string,
  symbolId: string,
  x: number,
  y: number,
  layerId = DEFAULT_LAYER_ID,
  props: Record<string, unknown> = {},
  extra: Partial<MimicElement> = {}
): MimicElement {
  return {
    id,
    symbolId,
    layerId,
    x,
    y,
    bindings: {},
    lockAspectRatio: symbolId.startsWith("custom:ps-tank") || symbolId.startsWith("custom:ps-valve"),
    props,
    ...extra,
  };
}

function conn(
  id: string,
  from: { elementId: string; port: string },
  to: { elementId: string; port: string },
  points: { x: number; y: number }[]
): MimicConnection {
  return { id, layerId: DEFAULT_LAYER_ID, from, to, points };
}

function rowSymbolId(rowColor: string): string {
  if (rowColor === "#66ccff") return "custom:ps-row-blue";
  if (rowColor === "#66ff66") return "custom:ps-row-green";
  return "custom:ps-row-yellow";
}

export function buildRpDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    el("hdr-title", "custom:ps-label", 8, 6, DEFAULT_LAYER_ID, {
      text: "СДКУ — Экранная форма РП",
      fontSize: 13,
      width: 220,
      height: 14,
    }),
    el("hdr-org", "custom:ps-label", 480, 6, DEFAULT_LAYER_ID, {
      text: "Магистральный нефтепровод",
      fontSize: 13,
      width: 240,
      height: 14,
    }),
    el("hdr-rdp", "custom:ps-label", 900, 6, DEFAULT_LAYER_ID, {
      text: "РДП «Центральный»",
      width: 120,
      height: 14,
    }),
    el("hdr-rp", "custom:ps-label", 1040, 6, DEFAULT_LAYER_ID, {
      text: "РП-1",
      width: 80,
      height: 14,
    }),
    el("hdr-col1", "custom:ps-label", 8, 34, DEFAULT_LAYER_ID, {
      text: "№ рез-ра",
      width: 60,
      height: 14,
    }),
    el("hdr-col2", "custom:ps-label", 52, 34, DEFAULT_LAYER_ID, {
      text: "Уровень нефти, мм",
      width: 120,
      height: 14,
    }),
    ...TANK_ROWS.map((row, i) =>
      el(`level-row-${row.num}`, rowSymbolId(row.rowColor), 8, 52 + i * 20, DEFAULT_LAYER_ID, {
        tankNum: String(row.num),
        width: 108,
        height: 18,
        tableExpand: "compact",
      }, {
        bindings: { value: tankBinding(row.num, "fillLevelMm") },
        actions: [{
          id: createMimicId("exp"),
          type: "toggleExpand",
          trigger: "primary",
          expandProp: "tableExpand",
        }],
        tooltip: { template: "Резервуар {tankNum}, Hнал мм" },
      })
    ),
    ...TANK_ROWS.map((row) => {
      const pos = TANK_POS[row.num];
      return el(`tank-${row.num}`, "custom:ps-tank", pos.x, pos.y, DEFAULT_LAYER_ID, {
        label: String(row.num),
        width: 76,
        height: 108,
        unitMode: "mm",
      }, {
        bindings: {
          fillLevel: tankBinding(row.num, "fillLevelMm"),
          maxLevel: tankBinding(row.num, "maxLevelMm"),
          rate: tankBinding(row.num, "rateMmPerHour"),
        },
        actions: [{
          id: createMimicId("unit"),
          type: "cycleUnit",
          trigger: "context",
          label: "Переключить единицы",
          order: 10,
          unitModes: ["mm", "m3", "t"],
        }],
        tooltip: { template: "Резервуар №{label}" },
      });
    }),
    ...VALVE_PLACEMENTS.map((v, i) => {
      const tankNum = TANK_ROWS[i % TANK_ROWS.length].num;
      return el(v.id, "custom:ps-valve", v.x, v.y, DEFAULT_LAYER_ID, {
        valveId: v.valveId,
        width: 24,
        height: 24,
      }, {
        bindings: { open: tankBinding(tankNum, "valveOpen") },
      });
    }),
    el("pipe-h-top", "custom:ps-pipe", 290, 116, DEFAULT_LAYER_ID, { width: 500, height: 8 }),
    el("pipe-h-mid", "custom:ps-pipe", 290, 348, DEFAULT_LAYER_ID, { width: 420, height: 8 }),
    el("pipe-h-bot", "custom:ps-pipe", 290, 580, DEFAULT_LAYER_ID, { width: 500, height: 8 }),
    ...STATIONS.map((s) =>
      el(`station-${s.label}`, "custom:ps-station", s.x, s.y, DEFAULT_LAYER_ID, {
        text: s.label,
        width: 120,
        height: 22,
      })
    ),
    el("summary-table", "custom:ps-summary", 1040, 380, DEFAULT_LAYER_ID, { width: 280, height: 120 }),
    el("hub-p-val", "value-badge", 1100, 516, DEFAULT_LAYER_ID, {
      width: 80,
      height: 20,
      unit: " МПа",
      decimals: 2,
    }, {
      bindings: {
        value: { objectPath: HUB, variableName: "linePressureMpa", valueField: "value", transform: "number" },
      },
    }),
    el("toggle-urdo", "custom:ps-nav-btn", 1040, 560, DEFAULT_LAYER_ID, {
      text: "Слой УРДО",
      width: 100,
      height: 22,
    }, {
      actions: [{
        id: createMimicId("urdo"),
        type: "toggleLayer",
        trigger: "primary",
        layerId: LAYER_URDO,
      }],
    }),
    el("urdo-marker", "custom:ps-label", 400, 640, LAYER_URDO, {
      text: "УРДО (слой справочной информации)",
      width: 260,
      height: 14,
    }),
    el("pipe-strip-bg", "custom:ps-panel", 24, 700, DEFAULT_LAYER_ID, { width: 1310, height: 48 }),
    el("pipe-strip-track", "pipeline-track", 60, 714, DEFAULT_LAYER_ID, {
      segments: 48,
      width: 1240,
      height: 20,
    }, {
      bindings: {
        seg0: { objectPath: HUB, variableName: "linePressureMpa", valueField: "value", transform: "number" },
      },
    }),
    ...PIPELINE_STATIONS.map((name, i) => {
      const step = 1240 / (PIPELINE_STATIONS.length - 1);
      const x = 60 + i * step;
      return el(`pipe-station-${i}`, "custom:ps-label", x - 30, 738, DEFAULT_LAYER_ID, {
        text: name,
        width: 80,
        height: 12,
        fontSize: 9,
      });
    }),
    ...pipelineNavElements("rp", RP_CANVAS.width, RP_CANVAS.height),
  ];

  const connections: MimicConnection[] = [];
  const trunkY = 116;
  TANK_ROWS.forEach((row) => {
    const pos = TANK_POS[row.num];
    const tankCx = pos.x + 38;
    const tankBottom = pos.y + 108;
    if (tankBottom < 650) {
      connections.push(
        conn(`drop-${row.num}`, { elementId: `tank-${row.num}`, port: "s" }, { elementId: "pipe-h-top", port: "w" }, [
          { x: tankCx, y: tankBottom },
          { x: tankCx, y: trunkY },
          { x: 290, y: trunkY },
        ])
      );
    }
  });

  return {
    version: 2,
    width: RP_CANVAS.width,
    height: RP_CANVAS.height,
    background: BG,
    typography: { fontFamily: FONT, fontSize: 12 },
    grid: { size: 1, snap: false, visible: false },
    layers: [
      { id: DEFAULT_LAYER_ID, name: "Основной", visible: true },
      { id: LAYER_URDO, name: "УРДО", visible: false },
    ],
    elements,
    connections,
    customSymbols: pipelineCustomSymbols(),
  };
}

/** @deprecated use buildRpDocument */
export const buildTankFarmDocument = buildRpDocument;
