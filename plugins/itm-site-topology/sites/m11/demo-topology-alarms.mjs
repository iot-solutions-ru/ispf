/**
 * Demo topology faults for M11 pilot — drives SVG link stroke / node fill via status bindings
 * and keeps hub.activeAlarmsFeed in sync (report «Активные аварии»).
 *
 * Usage:
 *   node demo-topology-alarms.mjs apply     # set demo faults (red links + alarm list)
 *   node demo-topology-alarms.mjs reset     # restore all green + empty alarm list
 *   node demo-topology-alarms.mjs sync      # rebuild alarm list from live status/linkStatus
 *   node demo-topology-alarms.mjs rotate    # cycle scenarios every 8s (Ctrl+C to stop)
 */
import { devicePath, topologyNodes } from "../../../itm-site-inventory/sites/m11/devices-catalog.mjs";

const BASE =
  process.env.ISPF_BASE_URL ??
  process.env.ISPF_DIRECT_URL ??
  "http://185.246.66.158:8080";
const USER = process.env.ISPF_USER ?? "admin";
const PASS = process.env.ISPF_PASS ?? "admin";
const HUB = "root.platform.devices.itm.hub";
const SITE = "m11";
const NETWORK = `root.platform.devices.itm.sites.${SITE}.network`;
const ISP = `root.platform.devices.itm.sites.${SITE}.isp`;
const SECTIONS = `root.platform.devices.itm.sites.${SITE}.sections`;

const argv = new Set(process.argv.slice(2));
const mode = argv.has("reset")
  ? "reset"
  : argv.has("rotate")
    ? "rotate"
    : argv.has("sync")
      ? "sync"
      : "apply";

/** Devices whose status.online drives many visible SVG links. */
const DEMO_FAULTS = [
  {
    path: `${NETWORK}.tp12`,
    label: "ТП12 offline",
    online: false,
    activeAlarms: 2,
  },
  {
    path: `${NETWORK}.deu19`,
    label: "ДЭУ19 offline",
    online: false,
    activeAlarms: 3,
  },
  {
    path: `${NETWORK}.tp16`,
    label: "ТП16 offline",
    online: false,
    activeAlarms: 1,
  },
  {
    path: `${SECTIONS}.section4.sw-section4`,
    label: "Участок 4 SW offline",
    online: false,
    activeAlarms: 1,
  },
  {
    path: `${ISP}.isp-rostelecom`,
    label: "Ростелеком link down",
    linkStatus: 0,
    activeAlarms: 1,
  },
  {
    path: `${ISP}.isp-megafon`,
    label: "Мегафон/Westcall link down",
    linkStatus: 0,
    activeAlarms: 1,
  },
];

const SCENARIOS = [
  {
    name: "east-fault",
    faults: DEMO_FAULTS.filter((f) => f.path.includes("tp12") || f.path.includes("deu19")),
    kpi: { kpiActiveAlarms: 5, kpiDevicesUp: 96, kpiAvailability: 98.2 },
  },
  {
    name: "west-fault",
    faults: DEMO_FAULTS.filter((f) => f.path.includes("tp16") || f.path.includes("section4")),
    kpi: { kpiActiveAlarms: 2, kpiDevicesUp: 98, kpiAvailability: 99.1 },
  },
  {
    name: "isp-fault",
    faults: DEMO_FAULTS.filter((f) => f.path.includes(".isp.")),
    kpi: { kpiActiveAlarms: 2, kpiDevicesUp: 99, kpiAvailability: 99.5 },
  },
  {
    name: "full-demo",
    faults: DEMO_FAULTS,
    kpi: { kpiActiveAlarms: 9, kpiDevicesUp: 93, kpiAvailability: 97.4 },
  },
];

const SECTION_IDS = ["section1", "section2", "section3", "section4", "section5", "section6"];
const ISP_IDS = ["isp-rostelecom", "isp-megafon"];

async function login() {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!res.ok) throw new Error(`login failed: ${res.status}`);
  return (await res.json()).token;
}

async function mcpSet(token, path, name, value) {
  const res = await fetch(`${BASE}/api/v1/ai/mcp`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      jsonrpc: "2.0",
      id: `${path}:${name}`,
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

async function ensureActiveAlarmsFeed(token) {
  const list = await fetch(
    `${BASE}/api/v1/objects/by-path/variables?path=${encodeURIComponent(HUB)}`,
    { headers: { Authorization: `Bearer ${token}` } }
  ).then((r) => r.json());
  if (list.some((v) => v.name === "activeAlarmsFeed")) return;

  const schema = {
    name: "activeAlarmsFeed",
    fields: [
      {
        name: "items",
        type: "RECORD_LIST",
        nestedSchema: {
          name: "alarmItem",
          fields: [
            { name: "ts", type: "STRING" },
            { name: "severity", type: "STRING" },
            { name: "kind", type: "STRING" },
            { name: "source", type: "STRING" },
            { name: "message", type: "STRING" },
            { name: "objectPath", type: "STRING" },
          ],
        },
      },
    ],
  };
  const res = await fetch(
    `${BASE}/api/v1/objects/by-path/variables?path=${encodeURIComponent(HUB)}`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        name: "activeAlarmsFeed",
        description: "Active topology / device alarms as a list (empty when healthy)",
        group: "status",
        readable: true,
        writable: true,
        historyEnabled: false,
        schema,
        value: { schema, rows: [{ items: [] }] },
      }),
    }
  );
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`create activeAlarmsFeed failed: ${res.status} ${text}`);
  }
  console.log("Created hub.activeAlarmsFeed");
}

async function setOnline(token, path, online, lastseen = "") {
  await mcpSet(token, path, "status", { online, lastseen: lastseen || new Date().toISOString() });
}

async function setInt(token, path, name, value) {
  await mcpSet(token, path, name, { value });
}

function candidatePaths() {
  const paths = [];
  for (const node of topologyNodes) {
    paths.push({
      path: devicePath({ zone: "network", id: node.id }),
      source: node.name,
    });
  }
  for (const section of SECTION_IDS) {
    paths.push({
      path: `${SECTIONS}.${section}.sw-${section}`,
      source: `Участок ${section.replace("section", "")}`,
    });
  }
  for (const ispId of ISP_IDS) {
    paths.push({
      path: `${ISP}.${ispId}`,
      source: ispId.includes("rostelecom") ? "Ростелеком" : "Мегафон/Westcall",
    });
  }
  return paths;
}

async function fetchVariablesBatch(token, paths) {
  const unique = [...new Set(paths)];
  const result = {};
  const chunkSize = 40;
  for (let i = 0; i < unique.length; i += chunkSize) {
    const chunk = unique.slice(i, i + chunkSize);
    const res = await fetch(
      `${BASE}/api/v1/objects/variables/batch?paths=${encodeURIComponent(chunk.join(","))}`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    if (!res.ok) throw new Error(`variables batch failed: ${res.status}`);
    Object.assign(result, await res.json());
  }
  return result;
}

function firstRow(vars, name) {
  const variable = vars?.find((v) => v.name === name);
  return variable?.value?.rows?.[0] ?? null;
}

/** Build alarm rows from live status / linkStatus (matches red topology links/nodes). */
async function buildAlarmsFromLiveState(token) {
  const candidates = candidatePaths();
  const batch = await fetchVariablesBatch(
    token,
    candidates.map((c) => c.path)
  );
  const ts = new Date().toISOString();
  /** @type {Array<Record<string, string>>} */
  const items = [];

  for (const candidate of candidates) {
    const vars = batch[candidate.path];
    if (!vars) continue;
    const status = firstRow(vars, "status");
    if (status && status.online === false) {
      items.push({
        ts,
        severity: "WARNING",
        kind: "node",
        source: candidate.source,
        message: `Узел offline: ${candidate.source}`,
        objectPath: candidate.path,
      });
    }
    const link = firstRow(vars, "linkStatus");
    if (link && (link.value === 0 || link.value === "0")) {
      items.push({
        ts,
        severity: "CRITICAL",
        kind: "link",
        source: candidate.source,
        message: `Линк down: ${candidate.source}`,
        objectPath: candidate.path,
      });
    }
  }
  return items;
}

async function writeAlarmsFeed(token, items) {
  await ensureActiveAlarmsFeed(token);
  await mcpSet(token, HUB, "activeAlarmsFeed", { items });
  await setInt(token, HUB, "kpiActiveAlarms", items.length);
  console.log(`  Alarms feed: ${items.length} active alarm(s)`);
}

async function syncFeed(token) {
  console.log("Syncing activeAlarmsFeed from live topology status…");
  const items = await buildAlarmsFromLiveState(token);
  await writeAlarmsFeed(token, items);
  for (const item of items) {
    console.log(`  ${item.severity} ${item.message}`);
  }
  if (items.length === 0) {
    console.log("  (no active alarms — list is empty)");
  }
}

async function resetAll(token) {
  console.log("Resetting topology devices to online…");
  for (const node of topologyNodes) {
    const path = devicePath({ zone: "network", id: node.id });
    try {
      await setOnline(token, path, true);
      await setInt(token, path, "activeAlarms", 0);
    } catch (e) {
      console.warn("  skip", path, e.message);
    }
  }
  for (const section of SECTION_IDS) {
    const path = `${SECTIONS}.${section}.sw-${section}`;
    try {
      await setOnline(token, path, true);
      await setInt(token, path, "activeAlarms", 0);
    } catch {
      /* optional */
    }
  }
  for (const ispId of ISP_IDS) {
    const path = `${ISP}.${ispId}`;
    try {
      await setInt(token, path, "linkStatus", 1);
      await setInt(token, path, "activeAlarms", 0);
    } catch {
      /* optional */
    }
  }
  await setInt(token, HUB, "kpiDevicesUp", 99);
  await mcpSet(token, HUB, "kpiAvailability", { value: 100.0 });
  await writeAlarmsFeed(token, []);
  console.log("Reset complete — all links green, alarm list empty.");
}

async function applyScenario(token, scenario) {
  console.log(`\n=== Scenario: ${scenario.name} ===`);
  await resetAll(token);

  for (const fault of scenario.faults) {
    try {
      if (fault.online === false) {
        await setOnline(token, fault.path, false, "demo-alarm");
        console.log(`  OFFLINE ${fault.label}`);
      }
      if (fault.linkStatus === 0) {
        await setInt(token, fault.path, "linkStatus", 0);
        console.log(`  LINK DOWN ${fault.label}`);
      }
      if (fault.activeAlarms) {
        await setInt(token, fault.path, "activeAlarms", fault.activeAlarms);
      }
    } catch (e) {
      console.error(`  FAIL ${fault.path}:`, e.message);
    }
  }

  const items = await buildAlarmsFromLiveState(token);
  await writeAlarmsFeed(token, items);

  const kpi = scenario.kpi ?? { kpiDevicesUp: 96, kpiAvailability: 98.0 };
  await setInt(token, HUB, "kpiDevicesUp", kpi.kpiDevicesUp);
  await mcpSet(token, HUB, "kpiAvailability", { value: kpi.kpiAvailability });
  console.log(`  KPI: alarms=${items.length}, devices up=${kpi.kpiDevicesUp}, SLA=${kpi.kpiAvailability}%`);
  console.log("Open DCN — red topology links/nodes should match «Активные аварии» rows.");
}

async function main() {
  const token = await login();
  await ensureActiveAlarmsFeed(token);

  if (mode === "reset") {
    await resetAll(token);
    return;
  }
  if (mode === "sync") {
    await syncFeed(token);
    return;
  }
  if (mode === "rotate") {
    let idx = 0;
    for (;;) {
      await applyScenario(token, SCENARIOS[idx % SCENARIOS.length]);
      idx++;
      await new Promise((r) => setTimeout(r, 8000));
    }
  }

  await applyScenario(token, SCENARIOS.find((s) => s.name === "full-demo") ?? SCENARIOS[0]);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
