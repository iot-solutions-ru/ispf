#!/usr/bin/env node
/**
 * Copy platform legal files into web-console public/ for Vite static serving.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "../../..");
const outDir = path.resolve(here, "../public/legal");

const copies = [
  ["LICENSE", "LICENSE"],
  ["NOTICE", "NOTICE"],
  ["docs/en/license.md", "LICENSE.md"],
  ["docs/en/third-party-notices.md", "THIRD_PARTY_NOTICES.md"],
  ["LICENSE-COMMERCIAL.md", "LICENSE-COMMERCIAL.md"],
  [
    "apps/web-console/src/scada/symbols/packs/ispf-pid-v1/LICENSE.md",
    "SYMBOL-PACK-PID-LICENSE.md",
  ],
];

fs.mkdirSync(outDir, { recursive: true });

for (const [srcRel, destName] of copies) {
  const src = path.join(repoRoot, srcRel);
  if (!fs.existsSync(src)) {
    console.error(`Missing legal source file: ${srcRel}`);
    process.exit(1);
  }
  fs.copyFileSync(src, path.join(outDir, destName));
}

console.log(`Copied ${copies.length} legal files to public/legal/`);
