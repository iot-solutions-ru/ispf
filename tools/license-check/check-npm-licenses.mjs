import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const lockPath = path.resolve(__dirname, "../../apps/web-console/package-lock.json");

const lock = JSON.parse(fs.readFileSync(lockPath, "utf8"));
const packages = lock.packages ?? {};
const unknown = [];

for (const [pkgPath, meta] of Object.entries(packages)) {
  if (!pkgPath || !meta || typeof meta !== "object") {
    continue;
  }
  const license = meta.license;
  if (license === "UNKNOWN" || license === "UNLICENSED") {
    unknown.push({ name: meta.name ?? pkgPath, version: meta.version ?? "", license });
  }
}

if (unknown.length) {
  console.error("npm packages with UNKNOWN/UNLICENSED license in package-lock.json:");
  for (const entry of unknown) {
    console.error(`  ${entry.name}@${entry.version} (${entry.license})`);
  }
  process.exit(1);
}

console.log("npm license check OK");
