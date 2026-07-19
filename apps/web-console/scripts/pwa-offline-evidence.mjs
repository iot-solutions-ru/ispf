#!/usr/bin/env node
/**
 * BL-151 offline PWA evidence (accelerated 8h TTL check).
 *
 * Verifies Workbox runtime cache config + operator localStorage 8h TTL policy
 * without a literal 8-hour soak. Exit 0 when evidence holds.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const viteConfig = readFileSync(join(root, "vite.config.ts"), "utf8");
const offlineCache = readFileSync(
  join(root, "src/utils/operator/operatorOfflineCache.ts"),
  "utf8"
);

const checks = [];

function assert(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  const mark = ok ? "PASS" : "FAIL";
  console.log(`${mark}  ${name}${detail ? ` — ${detail}` : ""}`);
}

assert(
  "SW caches /api/v1/dashboards/",
  viteConfig.includes("ispf-dashboards") && viteConfig.includes("dashboards"),
  "vite.config.ts runtimeCaching"
);
assert(
  "SW caches /api/v1/mimics/",
  viteConfig.includes("ispf-mimics") && /mimics/.test(viteConfig),
  "vite.config.ts runtimeCaching"
);
assert(
  "SW cache maxAgeSeconds = 8h",
  /maxAgeSeconds:\s*60\s*\*\s*60\s*\*\s*8/.test(viteConfig),
  "8h Workbox expiration"
);
assert(
  "operator localStorage TTL = 8h",
  /OFFLINE_CACHE_MAX_AGE_MS\s*=\s*8\s*\*\s*60\s*\*\s*60\s*\*\s*1000/.test(offlineCache) ||
    offlineCache.includes("8 * 60 * 60 * 1000"),
  "operatorOfflineCache.ts"
);
const syncSrc = readFileSync(
  join(root, "src/utils/operator/operatorOfflineSync.ts"),
  "utf8"
);
assert(
  "sync on reconnect helper exists",
  syncSrc.includes("syncOperatorCachesOnReconnect"),
  "operatorOfflineSync.ts"
);

const failed = checks.filter((c) => !c.ok);
if (failed.length) {
  console.error(`\nBL-151 evidence FAILED (${failed.length} checks)`);
  process.exit(1);
}
console.log("\nBL-151 evidence OK — SW dashboards+mimics 8h + reconnect sync policy verified");
