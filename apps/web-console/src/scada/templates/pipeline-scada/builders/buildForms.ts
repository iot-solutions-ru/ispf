/**
 * Detailed builders for pipeline SCADA forms (РД-029 §6.1–6.3, §6.6–6.15).
 */
import type { MimicElement, ScadaMimicDocument } from "../../../../types/scadaMimic";
import { createMimicId, DEFAULT_LAYER_ID } from "../../../document";
import { PIPELINE_FORMS, type PipelineFormKey } from "../paths";
import {
  drawMtTrunk,
  el,
  finishForm,
  formHeader,
  hubBinding,
  LAYER_GEO,
  LAYER_LABELS,
  LAYER_TABLE,
  MT_NODES,
  blockSymbol,
  trunkPipe,
} from "./buildCommon";

function navTo(key: PipelineFormKey) {
  const form = PIPELINE_FORMS[key];
  return {
    id: createMimicId("nav"),
    type: "navigate" as const,
    trigger: "primary" as const,
    label: form.title,
    dashboardPath: form.dashboardPath,
    mimicPath: form.mimicPath,
  };
}

function luNavStrip(y: number, prefix: string): MimicElement[] {
  const elements: MimicElement[] = [
    el(`${prefix}-lu-nav`, "custom:ps-lu-nav-strip", 40, y, undefined, { width: 1200, height: 48 }),
  ];
  MT_NODES.forEach((node, i) => {
    elements.push(
      el(`${prefix}-lu-node-${i}`, blockSymbol(node.kind), 60 + i * 145, y + 14, undefined, {
        text: node.label.replace("«Головная»", "").trim(),
        width: node.kind ? 72 : 90,
        height: 24,
      }, {
        actions: node.kind === "rp" ? [navTo("rp")] : node.kind === "sikn" ? [navTo("sikn")] : undefined,
      })
    );
  });
  return elements;
}

/** §6.1 Территориальная схема МТ */
export function buildMtTerritorialDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...formHeader("mtTerritorial"),
    ...drawMtTrunk(120, "mt"),
    el("mt-desc", "custom:ps-label", 40, 56, undefined, {
      text: "ОСТ — обобщённая территориальная схема магистральных трубопроводов",
      width: 500,
      height: 14,
      fontSize: 10,
    }),
    el("border-ost", "custom:ps-label", 40, 160, LAYER_GEO, {
      text: "— — — Граница ОСТ — — —",
      width: 200,
      height: 12,
      fontSize: 9,
    }),
    el("river", "custom:ps-label", 600, 200, LAYER_GEO, {
      text: "р. Иртыш",
      width: 80,
      height: 12,
      fontSize: 9,
    }),
    el("nav-rp", "custom:ps-nav-btn", 360, 88, undefined, { text: "→ РП", width: 60, height: 20 }, {
      actions: [navTo("rp")],
    }),
    el("nav-nps", "custom:ps-nav-btn", 920, 88, undefined, { text: "→ НПС", width: 60, height: 20 }, {
      actions: [navTo("nps")],
    }),
    el("nav-sea", "custom:ps-nav-btn", 1060, 88, undefined, { text: "→ Терминал", width: 80, height: 20 }, {
      actions: [navTo("seaTerminal")],
    }),
  ];
  return finishForm("mtTerritorial", elements, {
    layers: [
      { id: DEFAULT_LAYER_ID, name: "Основной", visible: true },
      { id: LAYER_GEO, name: "География", visible: true },
      { id: LAYER_LABELS, name: "Наименования", visible: true },
    ],
  });
}

/** §6.2 Схема МТ */
export function buildMtSchemeDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...formHeader("mtScheme"),
    ...drawMtTrunk(100, "sch", { showKm: true }),
    el("sch-rp-det", "custom:ps-block-rp", 340, 180, undefined, { text: "РП-1 (дет.)", width: 100, height: 40 }),
    el("sch-sikn-det", "custom:ps-block-sikn", 480, 180, undefined, { text: "СИКН-1", width: 100, height: 40 }),
    el("pto-os", "custom:ps-label", 200, 180, undefined, { text: "ПТО ОС", width: 60, height: 14 }),
    el("km-0", "custom:ps-km", 40, 130, undefined, { text: "0 км", width: 40, height: 14 }),
    el("km-720", "custom:ps-km", 1180, 130, undefined, { text: "720 км", width: 48, height: 14 }),
  ];
  return finishForm("mtScheme", elements, {
    layers: [
      { id: DEFAULT_LAYER_ID, name: "Основной", visible: true },
      { id: LAYER_GEO, name: "География", visible: false },
      { id: LAYER_LABELS, name: "Километраж", visible: true },
    ],
  });
}

/** §6.3 Размещение нефти в РП */
export function buildRpOilPlacementDocument(): ScadaMimicDocument {
  const corridors = ["МТ-1 / Север", "МТ-1 / Юг", "МТ-2", "Коридор-3", "Коридор-4", "Итого ОСТ"];
  const elements: MimicElement[] = [
    ...formHeader("rpOilPlacement"),
    el("placement-table", "custom:ps-placement-table", 40, 52, LAYER_TABLE, { width: 900, height: 320 }),
  ];
  corridors.forEach((name, i) => {
    elements.push(
      el(`row-${i}`, "custom:ps-label", 48, 88 + i * 28, LAYER_TABLE, {
        text: `${name}    125.4    118.2    42.1    +0.35    приём`,
        fontSize: 9,
        width: 880,
        height: 14,
      })
    );
  });
  elements.push(
    el("toggle-table", "custom:ps-nav-btn", 760, 380, undefined, {
      text: "Развернуть таблицу",
      width: 120,
      height: 22,
    }, {
      actions: [{ id: createMimicId("exp"), type: "toggleExpand", trigger: "primary", expandProp: "tableExpand" }],
    })
  );
  return finishForm("rpOilPlacement", elements, {
    layers: [
      { id: DEFAULT_LAYER_ID, name: "Основной", visible: true },
      { id: LAYER_TABLE, name: "Табличный", visible: true },
    ],
  });
}

/** §6.6 СИКН */
export function buildSiknDocument(): ScadaMimicDocument {
  const y = 140;
  const elements: MimicElement[] = [
    ...formHeader("sikn"),
    ...luNavStrip(48, "sikn"),
    el("flow-q", "custom:ps-flow-badge", 40, 100, undefined, { width: 160, height: 36 }, {
      bindings: { value: hubBinding("lineFlowM3h") },
    }),
    el("flow-int", "custom:ps-label", 220, 100, undefined, {
      text: "Qсут=12450 м³",
      width: 120,
      height: 14,
    }),
    trunkPipe(y, 80, 1200, "sikn-trunk"),
  ];
  ["ИЛ-1", "ИЛ-2", "ИЛ-3", "ИЛ-4"].forEach((il, i) => {
    const x = 120 + i * 260;
    elements.push(
      el(`il-${i}`, "custom:ps-label", x, y - 28, undefined, { text: il, width: 40, height: 12, fontSize: 9 }),
      el(`fgu-${i}`, "custom:ps-label", x + 40, y - 28, undefined, { text: "ФГУ", width: 32, height: 12, fontSize: 8 }),
      el(`v-${i}`, "custom:ps-valve", x + 80, y - 12, undefined, { valveId: String(201 + i), width: 24, height: 24 }),
      el(`rd-${i}`, "custom:ps-lamp", x + 120, y - 16, undefined, { width: 16, height: 16 }),
      el(`bik-${i}`, "custom:ps-label", x + 150, y - 28, undefined, { text: "БИК", width: 32, height: 12, fontSize: 8 }),
      el(`tpu-${i}`, "custom:ps-label", x + 190, y - 28, undefined, { text: "ТПУ", width: 32, height: 12, fontSize: 8 })
    );
  });
  elements.push(
    el("btn-extra", "custom:ps-nav-btn", 1040, 100, undefined, { text: "Доп. параметры", width: 110, height: 22 }),
    el("btn-2h", "custom:ps-nav-btn", 1040, 128, undefined, { text: "Инфо за 2 ч", width: 110, height: 22 })
  );
  return finishForm("sikn", elements);
}

/** §6.7 ПСП */
export function buildPspDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...formHeader("psp"),
    ...luNavStrip(48, "psp"),
    trunkPipe(160, 80, 1100, "psp-trunk"),
    el("psp-tank", "custom:ps-tank", 180, 200, undefined, { label: "ЕСУ", width: 76, height: 108 }),
    el("psp-tank2", "custom:ps-tank", 300, 200, undefined, { label: "ПСП", width: 76, height: 108 }),
    el("rp-link", "custom:ps-block-rp", 480, 120, undefined, { text: "РП-1", width: 90, height: 28 }, {
      actions: [navTo("rp")],
    }),
    el("sikn-link", "custom:ps-block-sikn", 620, 120, undefined, { text: "СИКН-1", width: 90, height: 28 }, {
      actions: [navTo("sikn")],
    }),
    el("p-in", "custom:ps-pressure", 400, 130, undefined, {}, {
      bindings: { value: hubBinding("linePressureMpa") },
    }),
    el("v-1", "custom:ps-valve", 520, 148, undefined, { valveId: "301", width: 24, height: 24 }),
    el("v-2", "custom:ps-valve", 660, 148, undefined, { valveId: "302", width: 24, height: 24 }),
    el("phase", "custom:ps-lamp", 800, 130, undefined, { width: 16, height: 16 }),
    el("phase-lbl", "custom:ps-label", 820, 130, undefined, { text: "Фазовое состояние", fontSize: 8, width: 120, height: 12 }),
  ];
  return finishForm("psp", elements);
}

/** §6.8 НПС */
export function buildNpsDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...formHeader("nps", { rdp: "НПС «Промежуточная»" }),
    el("alarm-panel", "custom:ps-panel", 1040, 48, undefined, { width: 280, height: 120 }),
    el("alarm-title", "custom:ps-label", 1050, 54, undefined, { text: "Аварии НПС", fontSize: 9, width: 100, height: 12 }),
    el("alarm-1", "custom:ps-alarm", 1050, 72, undefined, { text: "P min насоса", width: 140, height: 18 }),
    el("alarm-2", "custom:ps-alarm", 1050, 94, undefined, { text: "Т max подшипник", width: 140, height: 18 }),
    el("btn-detail", "custom:ps-nav-btn", 1200, 72, undefined, { text: "Подробно", width: 80, height: 22 }),
    el("btn-knp", "custom:ps-nav-btn", 1200, 100, undefined, { text: "КНП", width: 80, height: 22 }),
    trunkPipe(200, 100, 1000, "nps-suction"),
    trunkPipe(320, 100, 1000, "nps-discharge"),
  ];
  ["МНА-1", "МНА-2", "МНА-3", "ПНА-1", "ПНА-2"].forEach((label, i) => {
    const x = 120 + i * 180;
    elements.push(
      el(`pump-${i}`, "custom:ps-pump", x, 170, undefined, { width: 20, height: 20 }),
      el(`pump-lbl-${i}`, "custom:ps-label", x - 20, 148, undefined, { text: label, width: 60, height: 12, fontSize: 9 }),
      el(`v-in-${i}`, "custom:ps-valve", x, 218, undefined, { valveId: String(401 + i), width: 24, height: 24 }),
      el(`v-out-${i}`, "custom:ps-valve", x, 260, undefined, { valveId: String(411 + i), width: 24, height: 24 })
    );
  });
  elements.push(
    el("equip-table", "custom:ps-equip-table", 40, 380, LAYER_TABLE, { width: 420, height: 140 }),
    el("p-suct", "custom:ps-pressure", 80, 230, undefined, {}, {
      bindings: { value: hubBinding("linePressureMpa") },
    }),
    el("p-disc", "custom:ps-pressure", 80, 350, undefined, { text: "P=6.3" })
  );
  return finishForm("nps", elements, {
    layers: [
      { id: DEFAULT_LAYER_ID, name: "Основной", visible: true },
      { id: LAYER_TABLE, name: "Табличный", visible: true },
    ],
  });
}

/** §6.9 ЛУ МТ */
export function buildLuMtDocument(): ScadaMimicDocument {
  const y = 160;
  const elements: MimicElement[] = [
    ...formHeader("luMt"),
    ...luNavStrip(48, "lmt"),
    trunkPipe(y, 60, 1240, "lu-trunk"),
    el("nps", "custom:ps-block-nps", 80, y - 40, undefined, { text: "НПС-2", width: 90, height: 28 }),
  ];
  for (let i = 0; i < 8; i++) {
    const x = 200 + i * 130;
    elements.push(
      el(`kp-${i}`, "custom:ps-label", x, y - 36, undefined, { text: `КП-${i + 1}`, width: 40, height: 12, fontSize: 9 }),
      el(`v-${i}`, "custom:ps-valve", x + 10, y - 12, undefined, { valveId: String(501 + i), width: 24, height: 24 }),
      el(`dps-${i}`, "custom:ps-lamp", x + 40, y - 16, undefined, { width: 16, height: 16 }),
      el(`km-${i}`, "custom:ps-km", x, y + 20, undefined, { text: `${120 + i * 15} км`, width: 48, height: 14 }),
      el(`p-${i}`, "custom:ps-pressure", x + 50, y + 16, undefined, { width: 80, height: 24 })
    );
  }
  elements.push(
    el("kpp", "custom:ps-station", 900, y + 60, undefined, { text: "КПП СОД", width: 100, height: 22 }),
    el("well", "custom:ps-label", 720, y + 60, undefined, { text: "Колодец КИП", width: 80, height: 12, fontSize: 8 })
  );
  return finishForm("luMt", elements);
}

/** §6.10 Панель навигации по ЛУ МТ */
export function buildLuNavDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...formHeader("luNav"),
    el("nav-title", "custom:ps-label", 40, 48, undefined, {
      text: "Схематичное изображение МТ — переход на ЭФ ЛУ, НПС, СИКН, РП, ПСП",
      width: 700,
      height: 14,
      fontSize: 10,
    }),
    ...luNavStrip(80, "ln"),
    el("km-start", "custom:ps-km", 40, 140, undefined, { text: "0 км", width: 40, height: 14 }),
    el("km-end", "custom:ps-km", 1180, 140, undefined, { text: "720 км", width: 48, height: 14 }),
  ];
  MT_NODES.forEach((_node, i) => {
    elements.push(
      el(`seg-${i}`, "custom:ps-pipe", 60 + i * 145, 132, undefined, {
        width: i < MT_NODES.length - 1 ? 100 : 8,
        height: 8,
      })
    );
  });
  return finishForm("luNav", elements, { height: 400 });
}

/** §6.11 Морской терминал */
export function buildSeaTerminalDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...formHeader("seaTerminal"),
    el("rp", "custom:ps-block-rp", 80, 120, undefined, { text: "РП терминала", width: 110, height: 28 }, {
      actions: [navTo("rp")],
    }),
    el("sikn", "custom:ps-block-sikn", 220, 120, undefined, { text: "СИКН", width: 90, height: 28 }, {
      actions: [navTo("sikn")],
    }),
    trunkPipe(200, 80, 900, "sea-trunk"),
    el("tank-1", "custom:ps-tank", 360, 180, undefined, { label: "Р1", width: 76, height: 108 }),
    el("tank-2", "custom:ps-tank", 480, 180, undefined, { label: "Р2", width: 76, height: 108 }),
    el("pier-1", "custom:ps-pier", 680, 160, undefined, { text: "Причал-1", width: 80, height: 48 }, {
      actions: [navTo("pier")],
    }),
    el("pier-2", "custom:ps-pier", 820, 160, undefined, { text: "Причал-2", width: 80, height: 48 }),
    el("v-1", "custom:ps-valve", 600, 148, undefined, { valveId: "701", width: 24, height: 24 }),
    el("v-2", "custom:ps-valve", 760, 148, undefined, { valveId: "702", width: 24, height: 24 }),
  ];
  return finishForm("seaTerminal", elements);
}

/** §6.12 Причал */
export function buildPierDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...formHeader("pier"),
    el("rp", "custom:ps-block-rp", 80, 100, undefined, { text: "РП", width: 70, height: 28 }),
    el("sikn", "custom:ps-block-sikn", 180, 100, undefined, { text: "СИКН", width: 70, height: 28 }),
    trunkPipe(140, 140, 500, "pier-trunk"),
    el("pier", "custom:ps-pier", 520, 100, undefined, { text: "Причал №1", width: 100, height: 48 }),
    el("stender-1", "custom:ps-label", 540, 60, undefined, { text: "Стендер-1", width: 60, height: 12, fontSize: 8 }),
    el("stender-2", "custom:ps-label", 580, 60, undefined, { text: "Стендер-2", width: 60, height: 12, fontSize: 8 }),
    el("v-1", "custom:ps-valve", 480, 128, undefined, { valveId: "801", width: 24, height: 24 }),
    el("v-2", "custom:ps-valve", 560, 128, undefined, { valveId: "802", width: 24, height: 24 }),
    el("ship", "custom:ps-label", 640, 80, undefined, { text: "Танкер (символ)", width: 100, height: 14 }),
  ];
  return finishForm("pier", elements);
}

/** §6.13 Панель остановки МТ */
export function buildMtStopPanelDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...formHeader("mtStopPanel"),
    el("status-1", "custom:ps-status-panel", 40, 56, undefined, {
      title: "Участок ЛУ-1",
      text: "Состояние: норма",
      width: 400,
      height: 80,
    }),
    el("stop-1", "custom:ps-stop-btn", 460, 72, undefined, { text: "СТОП МНС", width: 90, height: 28 }),
    el("stop-2", "custom:ps-stop-btn", 560, 72, undefined, { text: "СТОП ПНС", width: 90, height: 28 }),
    el("status-2", "custom:ps-status-panel", 40, 160, undefined, {
      title: "Участок ЛУ-2",
      text: "Требуется остановка",
      width: 400,
      height: 80,
    }),
    el("stop-3", "custom:ps-stop-btn", 460, 176, undefined, { text: "СТОП МНС", width: 90, height: 28 }),
    el("stop-4", "custom:ps-stop-btn", 560, 176, undefined, { text: "СТОП ПНС", width: 90, height: 28 }),
    el("hint", "custom:ps-label", 40, 260, undefined, {
      text: "Панель остановки МТ — оперативная остановка технологического участка",
      width: 600,
      height: 14,
      fontSize: 10,
    }),
  ];
  return finishForm("mtStopPanel", elements, { height: 480 });
}

/** §6.14 Панель управления ЛЧ МТ */
export function buildMtSectionPanelDocument(): ScadaMimicDocument {
  const sections = ["ЛУ-1", "ЛУ-2", "ЛУ-3", "ЛУ-4"];
  const elements: MimicElement[] = [
    ...formHeader("mtSectionPanel"),
    el("sec-title", "custom:ps-label", 40, 48, undefined, {
      text: "Переключение технологического участка МТ",
      width: 320,
      height: 14,
    }),
  ];
  sections.forEach((sec, i) => {
    elements.push(
      el(`sec-btn-${i}`, "custom:ps-nav-btn", 40 + i * 130, 68, undefined, {
        text: sec,
        width: 110,
        height: 24,
      })
    );
  });
  elements.push(
    trunkPipe(180, 120, 1200, "sec-trunk"),
    el("rp", "custom:ps-block-rp", 200, 140, undefined, { text: "РП-1", width: 90, height: 40 }, { actions: [navTo("rp")] }),
    el("nps", "custom:ps-block-nps", 80, 140, undefined, { text: "НПС", width: 90, height: 40 }),
    el("sikn", "custom:ps-block-sikn", 360, 140, undefined, { text: "СИКН", width: 90, height: 40 }),
    el("mns", "custom:ps-pump", 520, 200, undefined, { width: 20, height: 20 }),
    el("mns-lbl", "custom:ps-label", 500, 178, undefined, { text: "МНС", width: 40, height: 12 }),
    el("pns", "custom:ps-pump", 620, 200, undefined, { width: 20, height: 20 }),
    el("pns-lbl", "custom:ps-label", 600, 178, undefined, { text: "ПНС", width: 40, height: 12 }),
    el("stop-mns", "custom:ps-stop-btn", 520, 240, undefined, { text: "Стоп МНС", width: 90, height: 28 }),
    el("stop-pns", "custom:ps-stop-btn", 620, 240, undefined, { text: "Стоп ПНС", width: 90, height: 28 }),
    el("v-1", "custom:ps-valve", 720, 168, undefined, { valveId: "901", width: 24, height: 24 }),
    el("flow", "custom:ps-flow-badge", 40, 120, undefined, { width: 140, height: 36 }, {
      bindings: { value: hubBinding("lineFlowM3h") },
    }),
    el("km", "custom:ps-km", 900, 220, undefined, { text: "240 км", width: 48, height: 14 })
  );
  return finishForm("mtSectionPanel", elements, { height: 480 });
}

/** §6.15 Панель управления НПС */
export function buildNpsPanelDocument(): ScadaMimicDocument {
  const elements: MimicElement[] = [
    ...formHeader("npsPanel"),
    el("panel-bg", "custom:ps-panel", 40, 56, undefined, { width: 1280, height: 200 }),
    el("panel-title", "custom:ps-label", 48, 64, undefined, {
      text: "НПС технологического участка МТ — Магистральный нефтепровод",
      width: 500,
      height: 14,
    }),
  ];
  ["МНС-1", "МНС-2", "МНС-3", "ПНС-1", "ПНС-2", "ПНС-3"].forEach((label, i) => {
    const x = 60 + i * 200;
    elements.push(
      el(`ctrl-${i}`, "custom:ps-ctrl-btn", x, 100, undefined, { text: `Упр. ${label}`, width: 110, height: 24 }),
      el(`pump-${i}`, "custom:ps-pump", x + 45, 140, undefined, { width: 20, height: 20 }),
      el(`lbl-${i}`, "custom:ps-label", x + 20, 168, undefined, { text: label, width: 70, height: 12, fontSize: 9 })
    );
  });
  elements.push(
    el("goto-nps", "custom:ps-nav-btn", 1100, 64, undefined, { text: "→ ЭФ НПС", width: 90, height: 22 }, {
      actions: [navTo("nps")],
    })
  );
  return finishForm("npsPanel", elements, { height: 480 });
}
