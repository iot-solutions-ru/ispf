import { mimicDocumentToJson } from "../document";
import { buildTankFarmDocument } from "./buildTankFarmMimic";

export const TANK_FARM_MIMIC_PATH = "root.platform.mimics.tank-farm-demo";
export const TANK_FARM_DASHBOARD_PATH = "root.platform.dashboards.tank-farm-hmi";

export const TANK_FARM_DOCUMENT = buildTankFarmDocument();
export const TANK_FARM_DOCUMENT_JSON = mimicDocumentToJson(TANK_FARM_DOCUMENT);
