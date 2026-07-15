import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  buildDeviceCatalog,
  devicePath,
  isTopologyPollDevice,
} from "./devices-catalog.mjs";
import { SNMP_CONFIG, mappingsForRole } from "./snmp-profiles.mjs";

const siteDir = path.dirname(fileURLToPath(import.meta.url));
const BASE =
  process.env.ISPF_BASE_URL ??
  process.env.ISPF_DIRECT_URL ??
  "http://185.246.66.158:8080";
const USER = process.env.ISPF_USER ?? "admin";
const PASS = process.env.ISPF_PASS ?? "admin";

/** 5 min default — 99×19 OID polls overload driver-ingress on pilot. */
const POLL_INTERVAL_MS = Number(process.env.ITM_SNMP_POLL_MS ?? 300_000);

const deviceCatalog = buildDeviceCatalog();
const argv = new Set(process.argv.slice(2));

async function login() {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!res.ok) throw new Error(`login failed: ${res.status}`);
  return (await res.json()).token;
}

async function importInventory(token) {
  const manifest = JSON.parse(
    fs.readFileSync(path.join(siteDir, "bundle.json"), "utf8")
  );
  for (let attempt = 1; attempt <= 3; attempt++) {
    const res = await fetch(
      `${BASE}/api/v1/platform/packages/import?packageId=itm-plugin-inventory-m11`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(manifest),
        signal: AbortSignal.timeout(300_000),
      }
    );
    const body = await res.text();
    console.log(`import (attempt ${attempt}):`, res.status, body.slice(0, 200));
    if (res.ok) return;
    if (attempt < 3) await new Promise((r) => setTimeout(r, 5000));
  }
  throw new Error("import failed after retries");
}

async function configureDriver(token, path, pointMappings) {
  const res = await fetch(
    `${BASE}/api/v1/drivers/runtime/configure?devicePath=${encodeURIComponent(path)}`,
    {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        driverId: "snmp",
        pollIntervalMs: POLL_INTERVAL_MS,
        configuration: SNMP_CONFIG,
        pointMappings,
        autoStart: false,
      }),
      signal: AbortSignal.timeout(60_000),
    }
  );
  const body = await res.text();
  if (!res.ok) throw new Error(`${res.status} ${body.slice(0, 300)}`);
}

async function setVariable(token, path, name, value) {
  const res = await fetch(`${BASE}/api/v1/ai/mcp`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      jsonrpc: "2.0",
      id: "set_variable",
      method: "tools/call",
      params: {
        name: "set_variable",
        arguments: { path, name, value },
      },
    }),
  });
  const json = await res.json();
  if (json.error) throw new Error(JSON.stringify(json.error));
  const text = json.result?.content?.[0]?.text;
  const parsed = text ? JSON.parse(text) : json.result;
  if (parsed?.status === "ERROR") throw new Error(parsed.error ?? "set_variable failed");
}

async function stopDriver(token, path) {
  const res = await fetch(
    `${BASE}/api/v1/drivers/runtime/stop?devicePath=${encodeURIComponent(path)}`,
    { method: "POST", headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(15_000) }
  );
  return res.ok;
}

async function startDriver(token, path) {
  const res = await fetch(
    `${BASE}/api/v1/drivers/runtime/start?devicePath=${encodeURIComponent(path)}`,
    { method: "POST", headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(15_000) }
  );
  return res.ok;
}

async function stopAllDrivers(token) {
  console.log(`Stopping SNMP drivers (${deviceCatalog.length} devices)...`);
  let stopped = 0;
  for (const device of deviceCatalog) {
    const path = devicePath(device);
    try {
      if (await stopDriver(token, path)) stopped++;
    } catch {
      /* already stopped */
    }
  }
  console.log(`Stopped ${stopped}/${deviceCatalog.length} drivers`);
}


async function main() {
  const token = await login();

  if (argv.has("--stop-all") && !argv.has("--skip-import") && !argv.has("--configure")) {
    await stopAllDrivers(token);
    return;
  }

  if (!argv.has("--skip-import")) {
    await importInventory(token);
  } else {
    console.log("skip-import: configuring drivers only");
  }

  let ok = 0;
  let fail = 0;

  for (const device of deviceCatalog) {
    const path = devicePath(device);
    const mappings = mappingsForRole(device.role, { lite: !argv.has("--full-oids") });
    const label = `${device.zone.split(".").pop()}/${device.id}`;

    try {
      await configureDriver(token, path, mappings);
      await setVariable(token, path, "snmpHost", { value: SNMP_CONFIG.host });
      await setVariable(token, path, "hostLabel", { value: label });
      await setVariable(token, path, "deviceRole", { value: device.role });
      if (device.svgId) {
        await setVariable(token, path, "topologyElementId", { value: device.svgId });
      }
      console.log(`OK [${device.role}]`, path);
      ok++;
    } catch (e) {
      console.error(`FAIL [${device.role}]`, path, e.message);
      fail++;
    }
  }

  console.log(`\nConfigured: ${ok} OK, ${fail} failed (${deviceCatalog.length} devices, poll=${POLL_INTERVAL_MS}ms)`);

  await stopAllDrivers(token);

  const startList = argv.has("--start-all")
    ? deviceCatalog
    : deviceCatalog.filter(isTopologyPollDevice);

  console.log(`Starting ${startList.length} SNMP driver(s) (topology subset unless --start-all)...`);
  let started = 0;
  for (const device of startList) {
    const path = devicePath(device);
    try {
      if (await startDriver(token, path)) started++;
    } catch {
      /* ignore */
    }
  }
  console.log(`Started ${started}/${startList.length} drivers`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
