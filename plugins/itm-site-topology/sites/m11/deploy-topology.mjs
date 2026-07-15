import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const siteDir = path.dirname(fileURLToPath(import.meta.url));
const BASE =
  process.env.ISPF_BASE_URL ??
  process.env.ISPF_DIRECT_URL ??
  "http://185.246.66.158:8080";
const USER = process.env.ISPF_USER ?? "admin";
const PASS = process.env.ISPF_PASS ?? "admin";
const MIMIC_PATH = "root.platform.mimics.itm-m11-dcn";

async function login() {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!res.ok) throw new Error(`login failed: ${res.status}`);
  return (await res.json()).token;
}

async function main() {
  const token = await login();
  const diagramJson = fs.readFileSync(path.join(siteDir, "mimic-diagram.json"), "utf8");

  const importRes = await fetch(
    `${BASE}/api/v1/platform/packages/import?packageId=itm-plugin-topology-m11`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: fs.readFileSync(path.join(siteDir, "bundle.json"), "utf8"),
      signal: AbortSignal.timeout(120_000),
    }
  );
  console.log("import:", importRes.status, (await importRes.text()).slice(0, 200));

  const mimicRes = await fetch(
    `${BASE}/api/v1/mimics/by-path/diagram?path=${encodeURIComponent(MIMIC_PATH)}`,
    {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ diagramJson }),
      signal: AbortSignal.timeout(300_000),
    }
  );
  const body = await mimicRes.text();
  console.log("mimic PUT:", mimicRes.status, body.slice(0, 300));
  if (!mimicRes.ok) throw new Error("mimic upload failed");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
