import { mimicDocumentToJson, parseMimicDocument } from "../document";
import miniTecMimic from "../../../../../packages/ispf-server/src/main/resources/bootstrap/mini-tec-mimic.json";

export const MINI_TEC_SLD_DOCUMENT = parseMimicDocument(JSON.stringify(miniTecMimic));
export const MINI_TEC_SLD_DOCUMENT_JSON = mimicDocumentToJson(MINI_TEC_SLD_DOCUMENT);
