#!/usr/bin/env node
/**
 * Generate examples/mini-tec/bundle.json — marketplace digital twin (GPU/GRPB/…).
 * Plant simulation: SQL state + schedule (no virtual driver profiles / no fixture seed).
 */
import { writeFileSync, readFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

const MEAS = {
  name: "measurement",
  fields: [
    { name: "value", type: "DOUBLE" },
    { name: "unit", type: "STRING" },
  ],
};
const BOOL = { name: "boolValue", fields: [{ name: "value", type: "BOOLEAN" }] };
const STR = { name: "stringValue", fields: [{ name: "value", type: "STRING" }] };
const INT = { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] };

function meas(name, description, group, unit, value) {
  return {
    name,
    description,
    group,
    schema: MEAS,
    readable: true,
    writable: false,
    defaultValue: { schema: MEAS, rows: [{ value, unit }] },
  };
}
function boolVar(name, description, group, value, writable = false) {
  return {
    name,
    description,
    group,
    schema: BOOL,
    readable: true,
    writable,
    defaultValue: { schema: BOOL, rows: [{ value }] },
  };
}
function intVar(name, description, group, value, writable = false) {
  return {
    name,
    description,
    group,
    schema: INT,
    readable: true,
    writable,
    defaultValue: { schema: INT, rows: [{ value }] },
  };
}
function strVar(name, description, group, value, writable = true) {
  return {
    name,
    description,
    group,
    schema: STR,
    readable: true,
    writable,
    defaultValue: { schema: STR, rows: [{ value }] },
  };
}

function driverVars() {
  return [
    strVar("driverId", "Driver id", "driver", "virtual"),
    strVar("driverConfigJson", "Driver config", "driver", "{}"),
    boolVar("driverAutoStart", "Auto-start", "driver", true, true),
  ];
}

const GPUS = [
  { code: "gpu-01", unit: 1, rated: 1480 },
  { code: "gpu-02", unit: 2, rated: 1480 },
  { code: "gpu-03", unit: 3, rated: 1480 },
];

const gpuVars = [
  meas("jacketWaterTemp", "Jacket water temperature", "telemetry", "C", 75),
  meas("jacketWaterPressure", "Jacket water pressure", "telemetry", "bar", 1.8),
  meas("lubeOilTemp", "Lube oil temperature", "telemetry", "C", 70),
  meas("lubeOilPressure", "Lube oil pressure", "telemetry", "bar", 4.5),
  meas("coolantTemp", "Coolant temperature", "telemetry", "C", 68),
  meas("exhaustGasTemp", "Exhaust gas temperature", "telemetry", "C", 420),
  meas("rpm", "Engine speed", "telemetry", "rpm", 0),
  meas("activePowerKw", "Active power", "telemetry", "kW", 0),
  meas("reactivePowerKvar", "Reactive power", "telemetry", "kVAr", 0),
  meas("runningHours", "Running hours", "exploitation", "h", 0),
  meas("energyKwh", "Active energy", "exploitation", "kWh", 0),
  meas("setpointPowerKw", "Power setpoint", "control", "kW", 1000),
  boolVar("running", "Running", "status", false),
  boolVar("synced", "Synchronized", "status", false),
  boolVar("detonation", "Detonation", "status", false),
  boolVar("cmdStart", "Start command", "control", false, true),
  boolVar("cmdStop", "Stop command", "control", false, true),
  intVar("startCount", "Start count", "exploitation", 0),
  ...driverVars(),
];

const grpbVars = [
  meas("gasOutletPressure", "Gas outlet pressure", "telemetry", "bar", 2.5),
  meas("gasFlowRate", "Gas flow", "telemetry", "m3/h", 700),
  meas("gasVolume", "Gas volume", "telemetry", "m3", 0),
  meas("valvePosition", "Valve position", "telemetry", "%", 80),
  boolVar("pzkTripped", "PZK tripped", "status", false),
  boolVar("gasLeak", "Gas leak", "status", false),
  boolVar("fireAlarm", "Fire alarm", "status", false),
  boolVar("cmdValveOpen", "Valve open cmd", "control", false, true),
  boolVar("cmdValveClose", "Valve close cmd", "control", false, true),
  boolVar("cmdGasTrip", "Gas trip cmd", "control", false, true),
  ...driverVars(),
];

const rumbVars = [
  boolVar("breakerClosed", "Breaker closed", "status", true),
  boolVar("interlockOk", "Interlock OK", "status", true),
  boolVar("emergencyStop", "E-stop", "status", false),
  boolVar("cmdBreakerClose", "Close cmd", "control", false, true),
  boolVar("cmdBreakerOpen", "Open cmd", "control", false, true),
  ...driverVars(),
];

const dguVars = [
  boolVar("running", "Running", "status", false),
  meas("fuelLevelPct", "Fuel level", "telemetry", "%", 85),
  meas("coolantTemp", "Coolant temp", "telemetry", "C", 25),
  meas("activePowerKw", "Active power", "telemetry", "kW", 0),
  boolVar("cmdStart", "Start", "control", false, true),
  boolVar("cmdStop", "Stop", "control", false, true),
  ...driverVars(),
];

const loadVars = [
  meas("activePowerKw", "Active power", "telemetry", "kW", 500),
  meas("reactivePowerKvar", "Reactive power", "telemetry", "kVAr", 60),
  meas("apparentPowerKva", "Apparent power", "telemetry", "kVA", 504),
  meas("frequencyHz", "Frequency", "telemetry", "Hz", 50),
  meas("loadSetpointPct", "Load setpoint", "control", "%", 35),
  boolVar("cmdSetLoad", "Apply load cmd", "control", false, true),
  ...driverVars(),
];

const tickSql = `
UPDATE gpu_sim SET
  cmd_start = false,
  cmd_stop = false,
  target_load_pct = CASE
    WHEN cmd_stop THEN 0
    WHEN cmd_start THEN LEAST(100, target_load_pct + 5)
    WHEN setpoint_kw > 0 THEN LEAST(100, setpoint_kw / NULLIF(rated_kw,0) * 100)
    ELSE target_load_pct END,
  load_pct = CASE
    WHEN load_pct < target_load_pct THEN LEAST(target_load_pct, load_pct + 2.5)
    WHEN load_pct > target_load_pct THEN GREATEST(target_load_pct, load_pct - 4.0)
    ELSE load_pct END,
  running = (load_pct > 2),
  start_count = start_count + CASE WHEN load_pct > 2 AND NOT was_running THEN 1 ELSE 0 END,
  was_running = (load_pct > 2),
  running_hours = running_hours + CASE WHEN load_pct > 2 THEN 0.001 ELSE 0 END,
  energy_kwh = energy_kwh + (rated_kw * load_pct / 100.0) / 3600.0 * 5,
  updated_at = NOW()
`.replace(/\s+/g, " ").trim();

const existingPath = join(__dirname, "bundle.json");
const previous = existsSync(existingPath)
  ? JSON.parse(readFileSync(existingPath, "utf8"))
  : {};

const objects = [
  {
    parentPath: "root.platform.devices",
    name: "mini-tec-plant",
    type: "CUSTOM",
    displayName: "Мини-ТЭЦ",
    description: "Marketplace mini-TEC plant",
  },
  ...GPUS.map((g) => ({
    parentPath: "root.platform.devices.mini-tec-plant",
    name: g.code,
    type: "DEVICE",
    displayName: g.code.toUpperCase(),
    templateId: "mini-tec-gpu-v1",
  })),
  {
    parentPath: "root.platform.devices.mini-tec-plant",
    name: "grpb",
    type: "DEVICE",
    displayName: "ГРПБ",
    templateId: "mini-tec-grpb-v1",
  },
  {
    parentPath: "root.platform.devices.mini-tec-plant",
    name: "rumb-10kv",
    type: "DEVICE",
    displayName: "РУМБ 10/0.4",
    templateId: "mini-tec-rumb-v1",
  },
  {
    parentPath: "root.platform.devices.mini-tec-plant",
    name: "dgu",
    type: "DEVICE",
    displayName: "ДГУ",
    templateId: "mini-tec-dgu-v1",
  },
  {
    parentPath: "root.platform.devices.mini-tec-plant",
    name: "load-module",
    type: "DEVICE",
    displayName: "Нагрузка",
    templateId: "mini-tec-load-module-v1",
  },
  {
    parentPath: "root.platform.devices.mini-tec-plant",
    name: "station-hub",
    type: "DEVICE",
    displayName: "Станционный hub",
    templateId: "mini-tec-station-hub-v1",
  },
];

const bindings = GPUS.flatMap((g) => [
  {
    objectPath: `root.platform.devices.mini-tec-plant.${g.code}`,
    variable: "activePowerKw",
    query: `SELECT (rated_kw * load_pct / 100.0) AS value, 'kW' AS unit FROM gpu_sim WHERE unit_code = '${g.code}'`,
    refreshIntervalMs: 2000,
    enabled: true,
  },
  {
    objectPath: `root.platform.devices.mini-tec-plant.${g.code}`,
    variable: "rpm",
    query: `SELECT CASE WHEN running THEN 1500 + load_pct * 0.5 ELSE 0 END AS value, 'rpm' AS unit FROM gpu_sim WHERE unit_code = '${g.code}'`,
    refreshIntervalMs: 2000,
    enabled: true,
  },
  {
    objectPath: `root.platform.devices.mini-tec-plant.${g.code}`,
    variable: "running",
    query: `SELECT running AS value FROM gpu_sim WHERE unit_code = '${g.code}'`,
    refreshIntervalMs: 2000,
    enabled: true,
  },
  {
    objectPath: `root.platform.devices.mini-tec-plant.${g.code}`,
    variable: "jacketWaterTemp",
    query: `SELECT (75 + load_pct * 0.25 + SIN(EXTRACT(EPOCH FROM NOW()) / 30.0) * 2) AS value, 'C' AS unit FROM gpu_sim WHERE unit_code = '${g.code}'`,
    refreshIntervalMs: 2000,
    enabled: true,
  },
  {
    objectPath: `root.platform.devices.mini-tec-plant.${g.code}`,
    variable: "runningHours",
    query: `SELECT running_hours AS value, 'h' AS unit FROM gpu_sim WHERE unit_code = '${g.code}'`,
    refreshIntervalMs: 5000,
    enabled: true,
  },
  {
    objectPath: `root.platform.devices.mini-tec-plant.${g.code}`,
    variable: "energyKwh",
    query: `SELECT energy_kwh AS value, 'kWh' AS unit FROM gpu_sim WHERE unit_code = '${g.code}'`,
    refreshIntervalMs: 5000,
    enabled: true,
  },
]);

const bundle = {
  version: "1.1.0",
  displayName: previous.displayName || "Мини-ТЭЦ (эталон)",
  tablePrefix: "",
  schemaName: "app_mini_tec",
  migrations: [
    ...(previous.migrations || []).filter((m) => m.id === "tec_schema"),
    {
      id: "tec_gpu_sim_v1",
      sql: `
CREATE TABLE IF NOT EXISTS gpu_sim (
  unit_code VARCHAR(32) PRIMARY KEY,
  unit_index INTEGER NOT NULL,
  rated_kw DOUBLE PRECISION NOT NULL DEFAULT 1480,
  load_pct DOUBLE PRECISION NOT NULL DEFAULT 70,
  target_load_pct DOUBLE PRECISION NOT NULL DEFAULT 70,
  setpoint_kw DOUBLE PRECISION NOT NULL DEFAULT 1000,
  running BOOLEAN NOT NULL DEFAULT true,
  was_running BOOLEAN NOT NULL DEFAULT true,
  start_count INTEGER NOT NULL DEFAULT 0,
  running_hours DOUBLE PRECISION NOT NULL DEFAULT 0,
  energy_kwh DOUBLE PRECISION NOT NULL DEFAULT 0,
  cmd_start BOOLEAN NOT NULL DEFAULT false,
  cmd_stop BOOLEAN NOT NULL DEFAULT false,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
${GPUS.map(
  (g) =>
    `INSERT INTO gpu_sim (unit_code, unit_index, rated_kw) SELECT '${g.code}', ${g.unit}, ${g.rated} WHERE NOT EXISTS (SELECT 1 FROM gpu_sim WHERE unit_code = '${g.code}');`
).join(" ")}
`.replace(/\s+/g, " ").trim(),
    },
  ],
  blueprints: [
    {
      name: "mini-tec-gpu-v1",
      description: "Gas piston unit",
      type: "INSTANCE",
      targetObjectType: "DEVICE",
      variables: gpuVars,
      events: [],
      functions: [],
      bindings: [],
      parameters: {},
    },
    {
      name: "mini-tec-grpb-v1",
      description: "Gas metering / block",
      type: "INSTANCE",
      targetObjectType: "DEVICE",
      variables: grpbVars,
      events: [],
      functions: [],
      bindings: [],
      parameters: {},
    },
    {
      name: "mini-tec-rumb-v1",
      description: "Switchgear",
      type: "INSTANCE",
      targetObjectType: "DEVICE",
      variables: rumbVars,
      events: [],
      functions: [],
      bindings: [],
      parameters: {},
    },
    {
      name: "mini-tec-dgu-v1",
      description: "Diesel genset",
      type: "INSTANCE",
      targetObjectType: "DEVICE",
      variables: dguVars,
      events: [],
      functions: [],
      bindings: [],
      parameters: {},
    },
    {
      name: "mini-tec-load-module-v1",
      description: "Load module",
      type: "INSTANCE",
      targetObjectType: "DEVICE",
      variables: loadVars,
      events: [],
      functions: [],
      bindings: [],
      parameters: {},
    },
    {
      name: "mini-tec-station-hub-v1",
      description: "Station hub / simulation scheduler host",
      type: "INSTANCE",
      targetObjectType: "DEVICE",
      variables: [...driverVars()],
      events: [],
      functions: [],
      bindings: [],
      parameters: {},
    },
  ],
  objects,
  functions: [
    {
      objectPath: "root.platform.devices.mini-tec-plant.station-hub",
      functionName: "mini_tec_sim_tick",
      version: "1",
      descriptor: {
        inputSchema: { name: "in", fields: [] },
        outputSchema: {
          name: "out",
          fields: [
            { name: "error_code", type: "STRING" },
            { name: "error_message", type: "STRING" },
          ],
        },
      },
      source: {
        type: "script",
        body: JSON.stringify({
          steps: [
            { type: "exec", sql: tickSql },
            { type: "return", fields: { error_code: "OK", error_message: "" } },
          ],
        }),
      },
    },
  ],
  bindings,
  schedules: [
    {
      scheduleId: "mini-tec-sim-tick",
      enabled: true,
      intervalMs: 5000,
      actionType: "invoke_function",
      action: {
        objectPath: "root.platform.devices.mini-tec-plant.station-hub",
        functionName: "mini_tec_sim_tick",
      },
    },
  ],
  reports: previous.reports || [],
  events: previous.events || [],
  dashboards: [
    {
      path: "root.platform.dashboards.mini-tec-overview",
      title: "Мини-ТЭЦ — обзор",
      refreshIntervalMs: 3000,
      layoutJson: JSON.stringify({
        columns: 84,
        rowHeight: 8,
        widgets: [
          {
            id: "g1",
            type: "value",
            title: "ГПУ-1 P",
            x: 0,
            y: 0,
            w: 21,
            h: 14,
            objectPath: "root.platform.devices.mini-tec-plant.gpu-01",
            variableName: "activePowerKw",
            decimals: 0,
          },
          {
            id: "g2",
            type: "value",
            title: "ГПУ-2 P",
            x: 21,
            y: 0,
            w: 21,
            h: 14,
            objectPath: "root.platform.devices.mini-tec-plant.gpu-02",
            variableName: "activePowerKw",
            decimals: 0,
          },
          {
            id: "g3",
            type: "value",
            title: "ГПУ-3 P",
            x: 42,
            y: 0,
            w: 21,
            h: 14,
            objectPath: "root.platform.devices.mini-tec-plant.gpu-03",
            variableName: "activePowerKw",
            decimals: 0,
          },
          {
            id: "rpm",
            type: "value",
            title: "ГПУ-1 RPM",
            x: 63,
            y: 0,
            w: 21,
            h: 14,
            objectPath: "root.platform.devices.mini-tec-plant.gpu-01",
            variableName: "rpm",
            decimals: 0,
          },
        ],
      }),
    },
  ],
  metadata: {
    description:
      "Marketplace digital twin for mini-TEC. Self-contained: blueprints, devices, SQL GPU sim tick, dashboards. Not seeded on empty platform.",
    changelog: "1.1.0 — extracted from platform fixtures; simulation via SQL schedule.",
  },
  operatorUi: {
    appId: "mini-tec",
    title: "Мини-ТЭЦ",
    defaultDashboard: "root.platform.dashboards.mini-tec-overview",
    dashboards: ["root.platform.dashboards.mini-tec-overview"],
    eventJournalObjectPath: "root.platform",
  },
};

writeFileSync(join(__dirname, "bundle.json"), JSON.stringify(bundle, null, 2) + "\n");
console.log("Wrote examples/mini-tec/bundle.json");
