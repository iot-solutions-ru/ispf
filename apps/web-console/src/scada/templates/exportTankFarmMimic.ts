import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { mimicDocumentToJson } from "../document";
import { buildTankFarmDocument } from "./buildTankFarmMimic";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(
  here,
  "../../../../../packages/ispf-server/src/main/resources/bootstrap/tank-farm-mimic.json"
);

const doc = buildTankFarmDocument();
fs.writeFileSync(out, mimicDocumentToJson(doc));
console.log(`Wrote ${out} (${doc.elements.length} elements, ${doc.connections.length} connections)`);
