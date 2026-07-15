import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(scriptDir, "../../..");
const BASE = process.env.ISPF_BASE_URL ?? "http://185.246.66.158:8080";
const USER = process.env.ISPF_USER ?? "admin";
const PASS = process.env.ISPF_PASS ?? "admin";

async function login() {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!res.ok) throw new Error(`login failed: ${res.status}`);
  return (await res.json()).token;
}

async function importBundle(token, packageId, bundlePath) {
  const body = fs.readFileSync(bundlePath, "utf8");
  const res = await fetch(
    `${BASE}/api/v1/platform/packages/import?packageId=${encodeURIComponent(packageId)}`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body,
      signal: AbortSignal.timeout(300_000),
    }
  );
  const text = await res.text();
  console.log(`${packageId}: HTTP ${res.status} ${text.slice(0, 160)}`);
  if (!res.ok) throw new Error(`import failed for ${packageId}`);
}

async function verifyLayout(token) {
  const res = await fetch(
    `${BASE}/api/v1/dashboards/by-path?path=${encodeURIComponent("root.platform.dashboards.itm-dcn")}`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (!res.ok) throw new Error(`dashboard fetch failed: ${res.status}`);
  const data = await res.json();
  const layout = JSON.parse(data.layoutJson ?? "{}");
  for (const widget of layout.widgets ?? []) {
    console.log(`  ${widget.id}: y=${widget.y} h=${widget.h}`);
  }
}

const token = await login();
await importBundle(
  token,
  "it-infra-monitoring",
  path.join(repo, "examples/it-infra-monitoring/bundle.json")
);
console.log("Verify itm-dcn layout:");
await verifyLayout(token);
