/**
 * Shared helpers for pipeline SCADA form builders (РД-029).
 */
import type {
  MimicBinding,
  MimicConnection,
  MimicElement,
  MimicLayer,
  ScadaMimicDocument,
} from "../../../../types/scadaMimic";
import { DEFAULT_LAYER_ID } from "../../../document";
import { pipelineCustomSymbols } from "../symbols";
import { PIPELINE_FORMS, PIPELINE_SCADA_HUB, type PipelineFormKey } from "../paths";
import { pipelineNavElements } from "./buildShell";

export const LAYER_GEO = "layer-geo";
export const LAYER_LABELS = "layer-labels";
export const LAYER_TABLE = "layer-table";
export const BG = "#c0c0c0";
export const FONT = "Arial, sans-serif";
export const DEFAULT_CANVAS = { width: 1360, height: 880 };

export function hubBinding(varName: string, transform: MimicBinding["transform"] = "number"): MimicBinding {
  return {
    objectPath: PIPELINE_SCADA_HUB,
    variableName: varName,
    valueField: "value",
    transform,
  };
}

export function el(
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
    props,
    ...extra,
  };
}

export function conn(
  id: string,
  from: { elementId: string; port: string },
  to: { elementId: string; port: string },
  points: { x: number; y: number }[]
): MimicConnection {
  return { id, layerId: DEFAULT_LAYER_ID, from, to, points };
}

export function formHeader(key: PipelineFormKey, extra?: Partial<{ rdp: string; org: string }>): MimicElement[] {
  const form = PIPELINE_FORMS[key];
  return [
    el("hdr-title", "custom:ps-label", 8, 6, DEFAULT_LAYER_ID, {
      text: `СДКУ — ${form.title}`,
      fontSize: 13,
      width: 420,
      height: 14,
    }),
    el("hdr-section", "custom:ps-label", 8, 26, DEFAULT_LAYER_ID, {
      text: `РД-029 §${form.section}`,
      fontSize: 11,
      width: 120,
      height: 14,
    }),
    el("hdr-org", "custom:ps-label", 480, 6, DEFAULT_LAYER_ID, {
      text: extra?.org ?? "Магистральный нефтепровод",
      fontSize: 13,
      width: 260,
      height: 14,
    }),
    el("hdr-rdp", "custom:ps-label", 900, 6, DEFAULT_LAYER_ID, {
      text: extra?.rdp ?? "РДП «Центральный»",
      width: 140,
      height: 14,
    }),
  ];
}

export function trunkPipe(y: number, x1: number, x2: number, id: string): MimicElement {
  return el(id, "custom:ps-pipe", x1, y, DEFAULT_LAYER_ID, { width: x2 - x1, height: 8 });
}

export function finishForm(
  key: PipelineFormKey,
  elements: MimicElement[],
  options: {
    width?: number;
    height?: number;
    layers?: MimicLayer[];
    connections?: MimicConnection[];
  } = {}
): ScadaMimicDocument {
  const width = options.width ?? DEFAULT_CANVAS.width;
  const height = options.height ?? DEFAULT_CANVAS.height;
  return {
    version: 2,
    width,
    height,
    background: BG,
    typography: { fontFamily: FONT, fontSize: 12 },
    grid: { size: 1, snap: false, visible: false },
    layers: options.layers ?? [{ id: DEFAULT_LAYER_ID, name: "Основной", visible: true }],
    elements: [...elements, ...pipelineNavElements(key, width, height)],
    connections: options.connections ?? [],
    customSymbols: pipelineCustomSymbols(),
  };
}

/** Demo nodes along the main pipeline (left → right). */
export const MT_NODES = [
  { id: "nps-h", label: "НПС «Головная»", x: 80, head: true },
  { id: "nps-2", label: "НПС-2", x: 220 },
  { id: "rp-1", label: "РП-1", x: 360, kind: "rp" as const },
  { id: "sikn-1", label: "СИКН-1", x: 500, kind: "sikn" as const },
  { id: "lu-1", label: "ЛУ-1", x: 640, kind: "lu" as const },
  { id: "psp-1", label: "ПСП-1", x: 780, kind: "psp" as const },
  { id: "nps-3", label: "НПС-3", x: 920 },
  { id: "sea", label: "Морской терминал", x: 1060, kind: "sea" as const },
];

export function blockSymbol(kind?: string): string {
  switch (kind) {
    case "rp":
      return "custom:ps-block-rp";
    case "sikn":
      return "custom:ps-block-sikn";
    case "lu":
      return "custom:ps-block-lu";
    case "psp":
      return "custom:ps-block-psp";
    case "sea":
      return "custom:ps-block-sea";
    default:
      return "custom:ps-block-nps";
  }
}

export function drawMtTrunk(
  y: number,
  prefix: string,
  opts: { showKm?: boolean; fromX?: number; toX?: number } = {}
): MimicElement[] {
  const fromX = opts.fromX ?? 40;
  const toX = opts.toX ?? 1280;
  const elements: MimicElement[] = [trunkPipe(y, fromX, toX, `${prefix}-trunk`)];
  MT_NODES.forEach((node, i) => {
    if (node.x < fromX || node.x > toX) return;
    elements.push(
      el(
        `${prefix}-${node.id}`,
        blockSymbol(node.kind),
        node.x - 50,
        y - 36,
        DEFAULT_LAYER_ID,
        { text: node.label, width: 100, height: 28, head: Boolean(node.head) }
      )
    );
    if (i < MT_NODES.length - 1) {
      const next = MT_NODES[i + 1];
      if (next.x <= toX) {
        elements.push(
          el(`${prefix}-tt-${i}`, "custom:ps-pipe", node.x + 50, y, DEFAULT_LAYER_ID, {
            width: Math.max(8, next.x - node.x - 100),
            height: 8,
          })
        );
      }
    }
    if (opts.showKm) {
      elements.push(
        el(`${prefix}-km-${i}`, "custom:ps-km", node.x - 20, y + 18, DEFAULT_LAYER_ID, {
          text: `${i * 120} км`,
          width: 48,
          height: 14,
        })
      );
    }
  });
  return elements;
}
