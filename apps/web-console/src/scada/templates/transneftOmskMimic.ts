import { mimicDocumentToJson, parseMimicDocument } from "../document";
import transneftOmskMimic from "../../../../../packages/ispf-server/src/main/resources/bootstrap/transneft-omsk-mimic.json";

export const TRANSNEFT_OMSK_MIMIC_PATH = "root.platform.mimics.transneft-omsk-rdp";
export const TRANSNEFT_OMSK_DASHBOARD_PATH = "root.platform.dashboards.transneft-omsk-rdp";

export const TRANSNEFT_OMSK_DOCUMENT = parseMimicDocument(JSON.stringify(transneftOmskMimic));
export const TRANSNEFT_OMSK_DOCUMENT_JSON = mimicDocumentToJson(TRANSNEFT_OMSK_DOCUMENT);
