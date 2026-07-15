/**
 * Reconfigure itm-active-alarms report to list hub.activeAlarmsFeed rows (real alarms).
 * Run from repo root after build-bundle, or standalone against pilot.
 */
const BASE = process.env.ISPF_BASE_URL ?? "http://185.246.66.158:8080";
const USER = process.env.ISPF_USER ?? "admin";
const PASS = process.env.ISPF_PASS ?? "admin";
const REPORT = "root.platform.reports.itm-active-alarms";

async function login() {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!res.ok) throw new Error(`login ${res.status}`);
  return (await res.json()).token;
}

const token = await login();
const columns = [
  { field: "severity", label: "Уровень" },
  { field: "source", label: "Источник" },
  { field: "message", label: "Сообщение" },
  { field: "objectPath", label: "Объект" },
  { field: "ts", label: "Время" },
];

const put = await fetch(
  `${BASE}/api/v1/reports/by-path/tree-variables-definition?path=${encodeURIComponent(REPORT)}`,
  {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      title: "Активные аварии",
      devicePathPattern: "root.platform.devices.itm.hub",
      variableName: "activeAlarmsFeed",
      columns,
      maxRows: 500,
      refreshIntervalMs: 5000,
    }),
  }
);
const text = await put.text();
console.log("report update:", put.status, text.slice(0, 200));
if (!put.ok) process.exit(1);

const run = await fetch(
  `${BASE}/api/v1/reports/by-path/run?path=${encodeURIComponent(REPORT)}`,
  {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: "{}",
  }
);
const preview = await run.json();
console.log("preview rows:", preview.rowCount, preview.rows?.slice?.(0, 3));
