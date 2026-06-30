import { mimicDocumentToJson } from "../document";
import { buildRpDocument } from "./pipeline-scada/builders/buildRp";
import { PIPELINE_FORMS, PIPELINE_SCADA_DASHBOARD } from "./pipeline-scada/paths";

export const PIPELINE_RP_MIMIC_PATH = PIPELINE_FORMS.rp.mimicPath;
export const PIPELINE_SCADA_HMI_DASHBOARD = PIPELINE_SCADA_DASHBOARD;
export const PIPELINE_RP_DOCUMENT = buildRpDocument();
export const PIPELINE_RP_DOCUMENT_JSON = mimicDocumentToJson(PIPELINE_RP_DOCUMENT);

/** @deprecated alias */
export const TANK_FARM_MIMIC_PATH = PIPELINE_RP_MIMIC_PATH;
