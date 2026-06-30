/**
 * Programmatic builder for the anonymized classic tank-farm SCADA mimic.
 * Export: npx tsx src/scada/templates/exportTankFarmMimic.ts
 */
import type {
  MimicBinding,
  MimicConnection,
  MimicCustomSymbol,
  MimicElement,
  ScadaMimicDocument,
} from "../../types/scadaMimic";
import { DEFAULT_LAYER_ID } from "../document";

const DEVICE = "root.platform.devices.tank-farm-demo";
const HUB = `${DEVICE}.manifold-hub`;

export const TANK_FARM_CANVAS = { width: 1360, height: 880 };
const BG = "#c0c0c0";

type TankRow = { num: number; levelMm: number; rowColor: string; productTypeA: boolean };

/** Demo snapshot values for the tank-farm showcase. */
export const TANK_ROWS: TankRow[] = [
  { num: 11, levelMm: 1662, rowColor: "#ffff66", productTypeA: true },
  { num: 12, levelMm: 1667, rowColor: "#ffff66", productTypeA: true },
  { num: 13, levelMm: 1481, rowColor: "#ffff66", productTypeA: true },
  { num: 14, levelMm: 1597, rowColor: "#ffff66", productTypeA: true },
  { num: 15, levelMm: 1620, rowColor: "#ffff66", productTypeA: true },
  { num: 16, levelMm: 6352, rowColor: "#66ccff", productTypeA: true },
  { num: 17, levelMm: 1762, rowColor: "#ffff66", productTypeA: true },
  { num: 18, levelMm: 11726, rowColor: "#66ccff", productTypeA: false },
  { num: 19, levelMm: 1702, rowColor: "#ffff66", productTypeA: false },
  { num: 20, levelMm: 5858, rowColor: "#66ccff", productTypeA: false },
  { num: 21, levelMm: 4393, rowColor: "#66ff66", productTypeA: false },
  { num: 22, levelMm: 1712, rowColor: "#ffff66", productTypeA: false },
  { num: 23, levelMm: 1280, rowColor: "#66ff66", productTypeA: false },
  { num: 24, levelMm: 1230, rowColor: "#66ff66", productTypeA: false },
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

const VALVE_PLACEMENTS: { id: string; valveId: string; x: number; y: number; open: boolean }[] = [
  { id: "v-222", valveId: "222", x: 318, y: 148, open: true },
  { id: "v-214", valveId: "214", x: 318, y: 264, open: false },
  { id: "v-459", valveId: "459", x: 318, y: 380, open: true },
  { id: "v-180", valveId: "180", x: 318, y: 496, open: true },
  { id: "v-301", valveId: "301", x: 500, y: 148, open: true },
  { id: "v-412", valveId: "412", x: 618, y: 148, open: false },
  { id: "v-527", valveId: "527", x: 700, y: 264, open: true },
  { id: "v-638", valveId: "638", x: 700, y: 380, open: true },
  { id: "v-749", valveId: "749", x: 700, y: 496, open: false },
  { id: "v-851", valveId: "851", x: 500, y: 380, open: true },
  { id: "v-963", valveId: "963", x: 380, y: 612, open: true },
  { id: "v-104", valveId: "104", x: 236, y: 612, open: true },
];

const STATIONS = [
  { label: "НПС-1", x: 340, y: 200 },
  { label: "КНС-2", x: 520, y: 260 },
  { label: "НПС-3", x: 620, y: 340 },
  { label: "Узел-4", x: 860, y: 200 },
];

const PIPELINE_STATIONS = ["СТ-1", "СТ-2", "СТ-3", "СТ-4", "СТ-5", "СТ-6"];

function tankPath(n: number) {
  return `${DEVICE}.tank-${n}`;
}

function tankBinding(n: number, varName: string): MimicBinding {
  return { objectPath: tankPath(n), variableName: varName, valueField: "value", transform: varName === "valveOpen" ? "bool" : "number" };
}

function el(
  id: string,
  symbolId: string,
  x: number,
  y: number,
  props: Record<string, unknown> = {},
  bindings: Record<string, MimicBinding> = {}
): MimicElement {
  return { id, symbolId, layerId: DEFAULT_LAYER_ID, x, y, props, bindings };
}

function conn(
  id: string,
  from: { elementId: string; port: string },
  to: { elementId: string; port: string },
  points: { x: number; y: number }[]
): MimicConnection {
  return { id, layerId: DEFAULT_LAYER_ID, from, to, points };
}

function customSymbols(): MimicCustomSymbol[] {
  return [
    {
      id: "tn-label",
      name: "TN label",
      width: 200,
      height: 16,
      viewBox: "0 0 200 16",
      svg: '<text id="ispf-text" x="0" y="12" fill="#000000" font-family="Tahoma,Arial,sans-serif" font-size="12">Label</text>',
      ports: [],
      bindingSchema: [{ key: "text", labelKey: "bindings.text", type: "string", optional: true }],
      behaviors: [{ bind: "text", type: "text", target: "#ispf-text", format: "string" }],
      sourceSymbolId: "label",
    },
    {
      id: "tn-tank",
      name: "TN tank",
      width: 76,
      height: 108,
      viewBox: "0 0 76 108",
      svg: [
        '<rect x="18" y="22" width="40" height="72" fill="#a8a8a8" stroke="#404040" stroke-width="1"/>',
        '<ellipse cx="38" cy="22" rx="22" ry="8" fill="#e8c840" stroke="#404040" stroke-width="1"/>',
        '<rect id="ispf-liquid" data-ispf-full-y="30" data-ispf-full-height="58" x="20" y="88" width="36" height="0" fill="#404040" opacity="0.9"/>',
        '<rect x="58" y="30" width="8" height="58" fill="#d8d8d8" stroke="#404040" stroke-width="0.5"/>',
        '<text id="ispf-label" x="38" y="14" text-anchor="middle" fill="#000" font-size="11" font-weight="bold">11</text>',
        '<text x="38" y="104" text-anchor="middle" fill="#000" font-size="8">H мм</text>',
        '<text id="ispf-rate" x="4" y="76" fill="#008000" font-size="8" font-family="monospace"></text>',
      ].join(""),
      ports: [
        { id: "n", x: 38, y: 14 },
        { id: "s", x: 38, y: 108 },
        { id: "e", x: 76, y: 58 },
        { id: "w", x: 0, y: 58 },
      ],
      bindingSchema: [
        { key: "fillLevel", labelKey: "bindings.fillLevel", type: "number" },
        { key: "maxLevel", labelKey: "bindings.maxLevel", type: "number", optional: true },
        { key: "rate", labelKey: "bindings.rate", type: "number", optional: true },
      ],
      behaviors: [
        { bind: "fillLevel", type: "fillLevel", target: "#ispf-liquid", maxBind: "maxLevel", inset: 0 },
        { bind: "label", type: "text", target: "#ispf-label", format: "string" },
        { bind: "rate", type: "text", target: "#ispf-rate", format: "number", suffix: " мм/ч" },
      ],
      sourceSymbolId: "tank.vertical",
    },
    {
      id: "tn-valve",
      name: "TN valve",
      width: 24,
      height: 24,
      viewBox: "0 0 24 24",
      svg: [
        '<line x1="12" y1="0" x2="12" y2="6" stroke="#fff" stroke-width="2"/>',
        '<line x1="12" y1="18" x2="12" y2="24" stroke="#fff" stroke-width="2"/>',
        '<polygon id="ispf-accent" points="4,12 12,6 20,12 12,18" fill="#c0c0c0" stroke="#000" stroke-width="1"/>',
        '<text id="ispf-valve-id" x="26" y="14" fill="#000" font-size="9" font-family="monospace">000</text>',
      ].join(""),
      ports: [
        { id: "n", x: 12, y: 0 },
        { id: "s", x: 12, y: 24 },
        { id: "e", x: 24, y: 12 },
        { id: "w", x: 0, y: 12 },
      ],
      bindingSchema: [{ key: "open", labelKey: "bindings.open", type: "boolean" }],
      behaviors: [
        { bind: "open", type: "stroke", target: "#ispf-accent", trueColor: "#00c000", falseColor: "#4080c0" },
        { bind: "valveId", type: "text", target: "#ispf-valve-id", format: "string" },
      ],
      sourceSymbolId: "valve.butterfly",
    },
    {
      id: "tn-pipe",
      name: "TN pipe",
      width: 100,
      height: 8,
      viewBox: "0 0 100 8",
      svg: '<line x1="0" y1="4" x2="100" y2="4" stroke="#ffffff" stroke-width="4"/>',
      ports: [
        { id: "w", x: 0, y: 4 },
        { id: "e", x: 100, y: 4 },
      ],
      bindingSchema: [],
      sourceSymbolId: "pipe.segment",
    },
    {
      id: "tn-pipe-v",
      name: "TN pipe vertical",
      width: 8,
      height: 100,
      viewBox: "0 0 8 100",
      svg: '<line x1="4" y1="0" x2="4" y2="100" stroke="#ffffff" stroke-width="4"/>',
      ports: [
        { id: "n", x: 4, y: 0 },
        { id: "s", x: 4, y: 100 },
      ],
      bindingSchema: [],
      sourceSymbolId: "pipe.segment",
    },
    {
      id: "tn-lamp",
      name: "TN status lamp",
      width: 16,
      height: 16,
      viewBox: "0 0 16 16",
      svg: '<circle id="ispf-accent" cx="8" cy="8" r="7" fill="#00c000" stroke="#004000" stroke-width="1"/>',
      ports: [],
      bindingSchema: [{ key: "active", labelKey: "bindings.active", type: "boolean", optional: true }],
      behaviors: [
        { bind: "active", type: "stroke", target: "#ispf-accent", trueColor: "#00c000", falseColor: "#808080" },
      ],
      sourceSymbolId: "sensor.indicator",
    },
    {
      id: "tn-row-yellow",
      name: "TN row yellow",
      width: 108,
      height: 18,
      viewBox: "0 0 108 18",
      svg: '<rect width="108" height="18" fill="#ffff66" stroke="#808080" stroke-width="0.5"/><text id="ispf-tank-num" x="4" y="13" fill="#000" font-size="10" font-family="Tahoma">11</text><text id="ispf-value" x="104" y="13" text-anchor="end" fill="#000" font-size="10" font-family="monospace" font-weight="bold">0</text>',
      ports: [],
      bindingSchema: [{ key: "value", labelKey: "bindings.value", type: "number" }],
      behaviors: [
        { bind: "tankNum", type: "text", target: "#ispf-tank-num", format: "string" },
        { bind: "value", type: "text", target: "#ispf-value", format: "number" },
      ],
      sourceSymbolId: "value-badge",
    },
    {
      id: "tn-row-blue",
      name: "TN row blue",
      width: 108,
      height: 18,
      viewBox: "0 0 108 18",
      svg: '<rect width="108" height="18" fill="#66ccff" stroke="#808080" stroke-width="0.5"/><text id="ispf-tank-num" x="4" y="13" fill="#000" font-size="10" font-family="Tahoma">16</text><text id="ispf-value" x="104" y="13" text-anchor="end" fill="#000" font-size="10" font-family="monospace" font-weight="bold">0</text>',
      ports: [],
      bindingSchema: [{ key: "value", labelKey: "bindings.value", type: "number" }],
      behaviors: [
        { bind: "tankNum", type: "text", target: "#ispf-tank-num", format: "string" },
        { bind: "value", type: "text", target: "#ispf-value", format: "number" },
      ],
      sourceSymbolId: "value-badge",
    },
    {
      id: "tn-row-green",
      name: "TN row green",
      width: 108,
      height: 18,
      viewBox: "0 0 108 18",
      svg: '<rect width="108" height="18" fill="#66ff66" stroke="#808080" stroke-width="0.5"/><text id="ispf-tank-num" x="4" y="13" fill="#000" font-size="10" font-family="Tahoma">21</text><text id="ispf-value" x="104" y="13" text-anchor="end" fill="#000" font-size="10" font-family="monospace" font-weight="bold">0</text>',
      ports: [],
      bindingSchema: [{ key: "value", labelKey: "bindings.value", type: "number" }],
      behaviors: [
        { bind: "tankNum", type: "text", target: "#ispf-tank-num", format: "string" },
        { bind: "value", type: "text", target: "#ispf-value", format: "number" },
      ],
      sourceSymbolId: "value-badge",
    },
    {
      id: "tn-station",
      name: "TN station box",
      width: 120,
      height: 22,
      viewBox: "0 0 120 22",
      svg: [
        '<rect width="120" height="22" fill="#d0d0d0" stroke="#606060" stroke-width="1"/>',
        '<text id="ispf-text" x="60" y="14" text-anchor="middle" fill="#000" font-size="9" font-family="Tahoma">Station</text>',
      ].join(""),
      ports: [],
      bindingSchema: [{ key: "text", labelKey: "bindings.text", type: "string", optional: true }],
      behaviors: [{ bind: "text", type: "text", target: "#ispf-text", format: "string" }],
      sourceSymbolId: "label",
    },
    {
      id: "tn-summary",
      name: "TN summary table",
      width: 280,
      height: 120,
      viewBox: "0 0 280 120",
      svg: [
        '<rect width="280" height="120" fill="#d8d8d8" stroke="#404040" stroke-width="1"/>',
        '<rect x="0" y="0" width="93" height="18" fill="#66ff66" stroke="#404040"/><text x="46" y="13" text-anchor="middle" fill="#000" font-size="9">Тип A</text>',
        '<rect x="93" y="0" width="94" height="18" fill="#ffcc66" stroke="#404040"/><text x="140" y="13" text-anchor="middle" fill="#000" font-size="9">Тип B</text>',
        '<rect x="187" y="0" width="93" height="18" fill="#c0c0c0" stroke="#404040"/><text x="233" y="13" text-anchor="middle" fill="#000" font-size="9">Всего</text>',
        '<text x="4" y="34" fill="#000" font-size="9">Наличие (тыс.т)</text><text x="80" y="34" text-anchor="end" fill="#000" font-size="9" font-family="monospace">59.0</text><text x="173" y="34" text-anchor="end" fill="#000" font-size="9" font-family="monospace">33.6</text><text x="276" y="34" text-anchor="end" fill="#000" font-size="9" font-family="monospace">92.6</text>',
        '<text x="4" y="52" fill="#000" font-size="9">Товар (тыс.т)</text><text x="80" y="52" text-anchor="end" fill="#000" font-size="9" font-family="monospace">42.2</text><text x="173" y="52" text-anchor="end" fill="#000" font-size="9" font-family="monospace">11.3</text><text x="276" y="52" text-anchor="end" fill="#000" font-size="9" font-family="monospace">53.5</text>',
        '<text x="4" y="70" fill="#000" font-size="9">Свободная (тыс.т)</text><text x="80" y="70" text-anchor="end" fill="#000" font-size="9" font-family="monospace">110.6</text><text x="173" y="70" text-anchor="end" fill="#000" font-size="9" font-family="monospace">200.3</text><text x="276" y="70" text-anchor="end" fill="#000" font-size="9" font-family="monospace">310.8</text>',
        '<rect x="4" y="82" width="272" height="18" fill="#b0b0b0" stroke="#606060"/><text x="140" y="94" text-anchor="middle" fill="#000" font-size="8">Показать/скрыть доп. панель</text>',
        '<text x="4" y="112" fill="#404040" font-size="8">Класс S (11–14) | Класс M (15–22) | Класс L (23–24)</text>',
      ].join(""),
      ports: [],
      bindingSchema: [],
      sourceSymbolId: "table.embedded",
    },
    {
      id: "tn-panel",
      name: "TN panel",
      width: 100,
      height: 48,
      viewBox: "0 0 100 48",
      svg: '<rect width="100" height="48" fill="#a8a8a8" stroke="#606060" stroke-width="1"/>',
      ports: [],
      bindingSchema: [],
      sourceSymbolId: "rect",
    },
    {
      id: "tn-pump",
      name: "TN pump",
      width: 20,
      height: 20,
      viewBox: "0 0 20 20",
      svg: [
        '<circle cx="10" cy="10" r="9" fill="#c0c0c0" stroke="#000" stroke-width="1"/>',
        '<line x1="3" y1="3" x2="17" y2="17" stroke="#000" stroke-width="1.5"/>',
        '<line x1="17" y1="3" x2="3" y2="17" stroke="#000" stroke-width="1.5"/>',
        '<circle cx="10" cy="10" r="3" fill="#00c000"/>',
      ].join(""),
      ports: [],
      bindingSchema: [],
      sourceSymbolId: "pump.centrifugal",
    },
  ];
}

function buildHeader(): MimicElement[] {
  return [
    el("hdr-time", "custom:tn-label", 8, 6, { text: "СКАДА / HMI", fontSize: 12, width: 120, height: 14 }),
    el("hdr-unit", "custom:tn-label", 140, 6, { text: "МПа", width: 40, height: 14 }),
    el("hdr-company", "custom:tn-label", 480, 6, { text: "Демо — нефтеперекачивающий узел", fontSize: 13, width: 260, height: 14 }),
    el("hdr-rdp", "custom:tn-label", 900, 6, { text: "Узел А-1", width: 80, height: 14 }),
    el("hdr-rp", "custom:tn-label", 1040, 6, { text: "Площадка Б", width: 100, height: 14 }),
    el("hdr-col1", "custom:tn-label", 8, 34, { text: "№ рез-ра", width: 60, height: 14 }),
    el("hdr-col2", "custom:tn-label", 52, 34, { text: "Уровень нефти, мм", width: 120, height: 14 }),
  ];
}

function rowSymbolId(rowColor: string): string {
  if (rowColor === "#66ccff") return "custom:tn-row-blue";
  if (rowColor === "#66ff66") return "custom:tn-row-green";
  return "custom:tn-row-yellow";
}

function buildLeftTable(): MimicElement[] {
  const out: MimicElement[] = [];
  TANK_ROWS.forEach((row, i) => {
    out.push(
      el(`level-row-${row.num}`, rowSymbolId(row.rowColor), 8, 52 + i * 20, {
        tankNum: String(row.num),
        width: 108,
        height: 18,
      }, {
        value: tankBinding(row.num, "fillLevelMm"),
      })
    );
  });
  return out;
}

function buildTanks(): MimicElement[] {
  return TANK_ROWS.map((row) => {
    const pos = TANK_POS[row.num];
    const stroke = row.productTypeA ? "#e8c840" : row.rowColor === "#66ff66" ? "#00a000" : "#4080c0";
    return el(`tank-${row.num}`, "custom:tn-tank", pos.x, pos.y, {
      label: String(row.num),
      tankStroke: stroke,
      liquidColor: "#303030",
      width: 76,
      height: 108,
    }, {
      fillLevel: tankBinding(row.num, "fillLevelMm"),
      maxLevel: tankBinding(row.num, "maxLevelMm"),
      rate: tankBinding(row.num, "rateMmPerHour"),
    });
  });
}

function buildValves(): MimicElement[] {
  return VALVE_PLACEMENTS.map((v, i) => {
    const tankNum = TANK_ROWS[i % TANK_ROWS.length].num;
    return el(v.id, "custom:tn-valve", v.x, v.y, { valveId: v.valveId, width: 24, height: 24 }, {
      open: tankBinding(tankNum, "valveOpen"),
    });
  });
}

function buildPipes(): MimicElement[] {
  const pipes: MimicElement[] = [];
  // Main horizontal collectors
  pipes.push(el("pipe-h-top", "custom:tn-pipe", 290, 116, { width: 500, height: 8 }));
  pipes.push(el("pipe-h-mid", "custom:tn-pipe", 290, 348, { width: 420, height: 8 }));
  pipes.push(el("pipe-h-bot", "custom:tn-pipe", 290, 580, { width: 500, height: 8 }));
  // Vertical risers left column
  for (let y = 116; y <= 580; y += 116) {
    pipes.push(el(`pipe-v-l-${y}`, "custom:tn-pipe-v", 248, y, { width: 8, height: 116 }));
  }
  // Vertical riser right column
  for (let y = 116; y <= 580; y += 116) {
    pipes.push(el(`pipe-v-r-${y}`, "custom:tn-pipe-v", 806, y, { width: 8, height: 116 }));
  }
  // Link 23-24
  pipes.push(el("pipe-v-23-24", "custom:tn-pipe-v", 166, 580, { width: 8, height: 80 }));
  pipes.push(el("pipe-h-23", "custom:tn-pipe", 166, 580, { width: 90, height: 8 }));
  return pipes;
}

function buildStationsAndLamps(): MimicElement[] {
  const out: MimicElement[] = [];
  STATIONS.forEach((s) => {
    out.push(el(`station-${s.label}`, "custom:tn-station", s.x, s.y, { text: s.label, width: 120, height: 22 }));
  });
  const lamps = [
    { id: "lamp-1", x: 490, y: 108 },
    { id: "lamp-2", x: 660, y: 168 },
    { id: "lamp-3", x: 740, y: 332 },
    { id: "lamp-4", x: 580, y: 448 },
    { id: "lamp-5", x: 840, y: 108 },
  ];
  lamps.forEach((l) => {
    out.push(el(l.id, "custom:tn-lamp", l.x, l.y, { width: 16, height: 16 }, {
      active: { objectPath: HUB, variableName: "linePressureMpa", valueField: "value", transform: "number" },
    }));
  });
  return out;
}

function buildSummary(): MimicElement[] {
  return [
    el("summary-table", "custom:tn-summary", 1040, 380, { width: 280, height: 120 }),
    el("hub-p-label", "custom:tn-label", 1040, 520, { text: "P линии:", width: 60, height: 14 }),
    el("hub-p-val", "value-badge", 1100, 516, { width: 80, height: 20, unit: " МПа", decimals: 2 }, {
      value: { objectPath: HUB, variableName: "linePressureMpa", valueField: "value", transform: "number" },
    }),
    el("hub-q-label", "custom:tn-label", 1040, 544, { text: "Q:", width: 30, height: 14 }),
    el("hub-q-val", "value-badge", 1070, 540, { width: 90, height: 20, unit: " м³/ч", decimals: 0 }, {
      value: { objectPath: HUB, variableName: "lineFlowM3h", valueField: "value", transform: "number" },
    }),
  ];
}

function buildPipelineStrip(): MimicElement[] {
  const out: MimicElement[] = [];
  const y = 700;
  out.push(el("pipe-strip-bg", "custom:tn-panel", 24, y, { width: 1310, height: 48 }));
  out.push(el("pipe-strip-track", "pipeline-track", 60, y + 14, { segments: 48, width: 1240, height: 20 }, {
    seg0: { objectPath: HUB, variableName: "linePressureMpa", valueField: "value", transform: "number" },
  }));
  const step = 1240 / (PIPELINE_STATIONS.length - 1);
  PIPELINE_STATIONS.forEach((name, i) => {
    const x = 60 + i * step;
    out.push(el(`pipe-station-${i}`, "custom:tn-label", x - 30, y + 38, { text: name, width: 80, height: 12, fontSize: 9 }));
    if (i > 0 && i < PIPELINE_STATIONS.length) {
      out.push(el(`pipe-pump-${i}`, "custom:tn-pump", x - 10, y + 2, { width: 20, height: 20 }));
    }
  });
  out.push(el("pipe-nav-l", "custom:tn-label", 4, y + 18, { text: "◀", width: 16, height: 16, fontSize: 14 }));
  out.push(el("pipe-nav-r", "custom:tn-label", 1336, y + 18, { text: "▶", width: 16, height: 16, fontSize: 14 }));
  // SIKn markers
  ["УЗР-01", "УЗР-02", "УЗР-03", "УЗР-04"].forEach((label, i) => {
    out.push(el(`sikn-${i}`, "custom:tn-label", 200 + i * 280, y - 14, { text: label, width: 70, height: 12, fontSize: 8 }));
  });
  return out;
}

function buildConnections(): MimicConnection[] {
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
  return connections;
}

export function buildTankFarmDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...buildHeader(),
    ...buildLeftTable(),
    ...buildTanks(),
    ...buildValves(),
    ...buildPipes(),
    ...buildStationsAndLamps(),
    ...buildSummary(),
    ...buildPipelineStrip(),
  ];

  return {
    version: 2,
    width: TANK_FARM_CANVAS.width,
    height: TANK_FARM_CANVAS.height,
    background: BG,
    grid: { size: 1, snap: false, visible: false },
    layers: [{ id: DEFAULT_LAYER_ID, name: "Main", visible: true }],
    elements,
    connections: buildConnections(),
    customSymbols: customSymbols(),
  };
}
