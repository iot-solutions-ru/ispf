/** Object paths for pipeline SCADA demo (РД-029 типовые ЭФ). */

export const PIPELINE_SCADA_APP = "pipeline-scada";
export const PIPELINE_SCADA_DEVICE = "root.platform.devices.pipeline-scada";
export const PIPELINE_SCADA_HUB = `${PIPELINE_SCADA_DEVICE}.manifold-hub`;

export const PIPELINE_SCADA_DASHBOARD = "root.platform.dashboards.pipeline-scada-hmi";

/** @deprecated alias — same as {@link PIPELINE_FORMS.rp.dashboardPath} */
export const TANK_FARM_MIMIC_PATH = "root.platform.mimics.pipeline-rp";
export const TANK_FARM_DASHBOARD_PATH = PIPELINE_SCADA_DASHBOARD;

export const PIPELINE_FORMS = {
  mtTerritorial: {
    id: "pipeline-mt-territorial",
    mimicPath: "root.platform.mimics.pipeline-mt-territorial",
    dashboardPath: "root.platform.dashboards.pipeline-mt-territorial",
    title: "Территориальная схема МТ",
    section: "6.1",
  },
  mtScheme: {
    id: "pipeline-mt-scheme",
    mimicPath: "root.platform.mimics.pipeline-mt-scheme",
    dashboardPath: "root.platform.dashboards.pipeline-mt-scheme",
    title: "Схема МТ",
    section: "6.2",
  },
  rpOilPlacement: {
    id: "pipeline-rp-oil-placement",
    mimicPath: "root.platform.mimics.pipeline-rp-oil-placement",
    dashboardPath: "root.platform.dashboards.pipeline-rp-oil-placement",
    title: "Размещение нефти в РП",
    section: "6.3",
  },
  rp: {
    id: "pipeline-rp",
    mimicPath: "root.platform.mimics.pipeline-rp",
    dashboardPath: "root.platform.dashboards.pipeline-scada-hmi",
    title: "Экранная форма РП",
    section: "6.4",
  },
  rpUrdo: {
    id: "pipeline-rp-urdo",
    mimicPath: "root.platform.mimics.pipeline-rp-urdo",
    dashboardPath: "root.platform.dashboards.pipeline-rp-urdo",
    title: "ЭФ РП со слоем УРДО",
    section: "6.5",
  },
  sikn: {
    id: "pipeline-sikn",
    mimicPath: "root.platform.mimics.pipeline-sikn",
    dashboardPath: "root.platform.dashboards.pipeline-sikn",
    title: "Экранная форма СИКН",
    section: "6.6",
  },
  psp: {
    id: "pipeline-psp",
    mimicPath: "root.platform.mimics.pipeline-psp",
    dashboardPath: "root.platform.dashboards.pipeline-psp",
    title: "Экранная форма ПСП",
    section: "6.7",
  },
  nps: {
    id: "pipeline-nps",
    mimicPath: "root.platform.mimics.pipeline-nps",
    dashboardPath: "root.platform.dashboards.pipeline-nps",
    title: "Экранная форма НПС",
    section: "6.8",
  },
  luMt: {
    id: "pipeline-lu-mt",
    mimicPath: "root.platform.mimics.pipeline-lu-mt",
    dashboardPath: "root.platform.dashboards.pipeline-lu-mt",
    title: "Экранная форма ЛУ МТ",
    section: "6.9",
  },
  luNav: {
    id: "pipeline-lu-nav",
    mimicPath: "root.platform.mimics.pipeline-lu-nav",
    dashboardPath: "root.platform.dashboards.pipeline-lu-nav",
    title: "Панель навигации по ЛУ МТ",
    section: "6.10",
  },
  seaTerminal: {
    id: "pipeline-sea-terminal",
    mimicPath: "root.platform.mimics.pipeline-sea-terminal",
    dashboardPath: "root.platform.dashboards.pipeline-sea-terminal",
    title: "Морской терминал",
    section: "6.11",
  },
  pier: {
    id: "pipeline-pier",
    mimicPath: "root.platform.mimics.pipeline-pier",
    dashboardPath: "root.platform.dashboards.pipeline-pier",
    title: "Причал",
    section: "6.12",
  },
  mtStopPanel: {
    id: "pipeline-mt-stop-panel",
    mimicPath: "root.platform.mimics.pipeline-mt-stop-panel",
    dashboardPath: "root.platform.dashboards.pipeline-mt-stop-panel",
    title: "Панель остановки МТ",
    section: "6.13",
  },
  mtSectionPanel: {
    id: "pipeline-mt-section-panel",
    mimicPath: "root.platform.mimics.pipeline-mt-section-panel",
    dashboardPath: "root.platform.dashboards.pipeline-mt-section-panel",
    title: "Панель управления ЛЧ МТ",
    section: "6.14",
  },
  npsPanel: {
    id: "pipeline-nps-panel",
    mimicPath: "root.platform.mimics.pipeline-nps-panel",
    dashboardPath: "root.platform.dashboards.pipeline-nps-panel",
    title: "Панель управления НПС",
    section: "6.15",
  },
} as const;

export type PipelineFormKey = keyof typeof PIPELINE_FORMS;

export const ALL_PIPELINE_FORM_KEYS = Object.keys(PIPELINE_FORMS) as PipelineFormKey[];

export function tankPath(n: number): string {
  return `${PIPELINE_SCADA_DEVICE}.tank-${n}`;
}
