/**
 * Generic shell for pipeline SCADA forms (РД-029 §6.1–6.3, §6.6–6.15).
 * Full detail for §6.4–6.5 is in buildRp.ts.
 */
import type { MimicAction, MimicElement, ScadaMimicDocument } from "../../../../types/scadaMimic";
import { createMimicId, DEFAULT_LAYER_ID } from "../../../document";
import { pipelineCustomSymbols } from "../symbols";
import {
  ALL_PIPELINE_FORM_KEYS,
  PIPELINE_FORMS,
  type PipelineFormKey,
} from "../paths";

const BG = "#c0c0c0";
const FONT = "Arial, sans-serif";
const DEFAULT_SIZE = { width: 1360, height: 880 };

const NAV_FORMS: PipelineFormKey[] = [
  "mtTerritorial",
  "mtScheme",
  "rpOilPlacement",
  "rp",
  "sikn",
  "nps",
  "luMt",
  "seaTerminal",
];

function el(
  id: string,
  symbolId: string,
  x: number,
  y: number,
  props: Record<string, unknown> = {},
  actions?: MimicAction[]
): MimicElement {
  return {
    id,
    symbolId,
    layerId: DEFAULT_LAYER_ID,
    x,
    y,
    bindings: {},
    props,
    actions,
  };
}

function navAction(target: PipelineFormKey): MimicAction {
  const form = PIPELINE_FORMS[target];
  return {
    id: createMimicId("nav"),
    type: "navigate",
    trigger: "primary",
    label: form.title,
    dashboardPath: form.dashboardPath,
    mimicPath: form.mimicPath,
  };
}

export function pipelineNavElements(
  currentKey: PipelineFormKey,
  _width: number,
  height: number
): MimicElement[] {
  const navY = height - 72;
  const navW = 150;
  const navGap = 6;
  const navCols = 4;
  const elements: MimicElement[] = [];

  NAV_FORMS.forEach((navKey, i) => {
    const col = i % navCols;
    const row = Math.floor(i / navCols);
    const target = PIPELINE_FORMS[navKey];
    const isCurrent = navKey === currentKey;
    elements.push(
      el(
        `nav-${navKey}`,
        "custom:ps-nav-btn",
        24 + col * (navW + navGap),
        navY + row * 28,
        {
          text: isCurrent ? `▶ ${target.title}` : target.title,
          width: navW,
          height: 22,
        },
        [navAction(navKey)]
      )
    );
  });

  if (!NAV_FORMS.includes(currentKey)) {
    const target = PIPELINE_FORMS[currentKey];
    elements.push(
      el("nav-current", "custom:ps-nav-btn", 24, navY - 32, {
        text: `▶ ${target.title}`,
        width: navW + 40,
        height: 22,
      })
    );
  }

  return elements;
}

export function buildPipelineFormShell(
  key: PipelineFormKey,
  size: Partial<{ width: number; height: number }> = {}
): ScadaMimicDocument {
  const form = PIPELINE_FORMS[key];
  const width = size.width ?? DEFAULT_SIZE.width;
  const height = size.height ?? DEFAULT_SIZE.height;

  const elements: MimicElement[] = [
    el("hdr-title", "custom:ps-label", 8, 8, {
      text: `СДКУ — ${form.title}`,
      fontSize: 13,
      width: 400,
      height: 16,
    }),
    el("hdr-section", "custom:ps-label", 8, 28, {
      text: `РД-029 §${form.section}`,
      fontSize: 11,
      width: 160,
      height: 14,
    }),
    el("hdr-org", "custom:ps-label", 480, 8, {
      text: "Магистральный нефтепровод",
      fontSize: 13,
      width: 240,
      height: 14,
    }),
    el("hdr-rdp", "custom:ps-label", 900, 8, {
      text: "РДП «Центральный»",
      width: 140,
      height: 14,
    }),
    el("content-panel", "custom:ps-panel", 24, 56, { width: width - 48, height: height - 180 }),
    el("content-hint", "custom:ps-label", 40, 80, {
      text: `${form.title} — типовая экранная форма (демо-заглушка)`,
      width: width - 80,
      height: 14,
    }),
    el("content-note", "custom:ps-label", 40, 100, {
      text: "Полная детализация по РД-029 — в следующих итерациях. Навигация между формами — кнопки ниже.",
      fontSize: 10,
      width: width - 80,
      height: 28,
    }),
    ...pipelineNavElements(key, width, height),
  ];

  return {
    version: 2,
    width,
    height,
    background: BG,
    typography: { fontFamily: FONT, fontSize: 12 },
    grid: { size: 1, snap: false, visible: false },
    layers: [{ id: DEFAULT_LAYER_ID, name: "Основной", visible: true }],
    elements,
    connections: [],
    customSymbols: pipelineCustomSymbols(),
  };
}

export { ALL_PIPELINE_FORM_KEYS, PIPELINE_FORMS };
