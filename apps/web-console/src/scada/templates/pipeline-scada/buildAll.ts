import { buildRpDocument } from "./builders/buildRp";
import {
  buildLuMtDocument,
  buildLuNavDocument,
  buildMtSchemeDocument,
  buildMtSectionPanelDocument,
  buildMtStopPanelDocument,
  buildMtTerritorialDocument,
  buildNpsDocument,
  buildNpsPanelDocument,
  buildPierDocument,
  buildPspDocument,
  buildRpOilPlacementDocument,
  buildSeaTerminalDocument,
  buildSiknDocument,
} from "./builders/buildForms";
import {
  ALL_PIPELINE_FORM_KEYS,
  PIPELINE_FORMS,
  type PipelineFormKey,
} from "./paths";
import type { ScadaMimicDocument } from "../../../types/scadaMimic";

const BUILDERS: Record<PipelineFormKey, () => ScadaMimicDocument> = {
  mtTerritorial: () => buildMtTerritorialDocument(),
  mtScheme: () => buildMtSchemeDocument(),
  rpOilPlacement: () => buildRpOilPlacementDocument(),
  rp: () => buildRpDocument(),
  rpUrdo: () => {
    const doc = buildRpDocument();
    return {
      ...doc,
      layers: doc.layers.map((l) => (l.id === "layer-urdo" ? { ...l, visible: true } : l)),
    };
  },
  sikn: () => buildSiknDocument(),
  psp: () => buildPspDocument(),
  nps: () => buildNpsDocument(),
  luMt: () => buildLuMtDocument(),
  luNav: () => buildLuNavDocument(),
  seaTerminal: () => buildSeaTerminalDocument(),
  pier: () => buildPierDocument(),
  mtStopPanel: () => buildMtStopPanelDocument(),
  mtSectionPanel: () => buildMtSectionPanelDocument(),
  npsPanel: () => buildNpsPanelDocument(),
};

export function buildPipelineForm(key: PipelineFormKey): ScadaMimicDocument {
  return BUILDERS[key]();
}

export function allPipelineDocuments(): { key: PipelineFormKey; doc: ScadaMimicDocument }[] {
  return ALL_PIPELINE_FORM_KEYS.map((key) => ({ key, doc: BUILDERS[key]() }));
}

export { PIPELINE_FORMS };
