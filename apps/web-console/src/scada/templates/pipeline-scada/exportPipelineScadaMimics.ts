/**
 * Export all 15 pipeline SCADA mimic documents to server bootstrap resources.
 * Run: npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { mimicDocumentToJson } from "../../document";
import { allPipelineDocuments } from "./buildAll";
import { PIPELINE_FORMS } from "./paths";

const here = path.dirname(fileURLToPath(import.meta.url));
const bootstrapDir = path.resolve(
  here,
  "../../../../../../packages/ispf-server/src/main/resources/bootstrap"
);

if (!fs.existsSync(bootstrapDir)) {
  fs.mkdirSync(bootstrapDir, { recursive: true });
}

for (const { key, doc } of allPipelineDocuments()) {
  const form = PIPELINE_FORMS[key];
  const out = path.join(bootstrapDir, `${form.id}-mimic.json`);
  fs.writeFileSync(out, mimicDocumentToJson(doc));
  console.log(`Wrote ${out} (${doc.elements.length} elements)`);
}

console.log(`Exported ${allPipelineDocuments().length} pipeline SCADA mimics.`);
