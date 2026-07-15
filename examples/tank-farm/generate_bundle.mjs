#!/usr/bin/env node
/**
 * Generate examples/tank-farm/bundle.json — marketplace tank-farm SCADA demo.
 * Simulation lives in app SQL + schedule (not virtual driver profiles).
 */
import { writeFileSync } from "node:fs";
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
const BOOL = {
  name: "boolValue",
  fields: [{ name: "value", type: "BOOLEAN" }],
};
const STR = {
  name: "stringValue",
  fields: [{ name: "value", type: "STRING" }],
};

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

function driverVars() {
  return [
    {
      name: "driverId",
      description: "Driver id",
      group: "driver",
      schema: STR,
      readable: true,
      writable: true,
      defaultValue: { schema: STR, rows: [{ value: "virtual" }] },
    },
    {
      name: "driverConfigJson",
      description: "Driver config (OOTB virtual; domain sim via SQL tick)",
      group: "driver",
      schema: STR,
      readable: true,
      writable: true,
      defaultValue: { schema: STR, rows: [{ value: "{}" }] },
    },
    {
      name: "driverAutoStart",
      description: "Auto-start driver",
      group: "driver",
      schema: BOOL,
      readable: true,
      writable: true,
      defaultValue: { schema: BOOL, rows: [{ value: true }] },
    },
  ];
}

const TANKS = [
  { n: 11, level: 1662, rate: -430 },
  { n: 12, level: 1667, rate: 85 },
  { n: 13, level: 1481, rate: -120 },
  { n: 14, level: 1597, rate: 40 },
  { n: 15, level: 1620, rate: -210 },
  { n: 16, level: 6352, rate: 15 },
  { n: 17, level: 1762, rate: -55 },
  { n: 18, level: 11726, rate: 220 },
  { n: 19, level: 1702, rate: -180 },
];

const inserts = TANKS.map(
  (t) =>
    `INSERT INTO tank_sim (tank_code, level_mm, rate_mm_per_h, max_level_mm) SELECT 'tank-${t.n}', ${t.level}, ${t.rate}, 12000 WHERE NOT EXISTS (SELECT 1 FROM tank_sim WHERE tank_code = 'tank-${t.n}');`
).join(" ");

const tickSql = `
UPDATE tank_sim
SET rate_mm_per_h = rate_bias_mm_per_h + SIN(EXTRACT(EPOCH FROM NOW()) / 45.0 + tank_index * 0.7) * 35,
    level_mm = GREATEST(500, LEAST(max_level_mm - 200, level_mm + (rate_bias_mm_per_h + SIN(EXTRACT(EPOCH FROM NOW()) / 45.0 + tank_index * 0.7) * 35) / 3600.0 * 5)),
    valve_open = ABS(rate_bias_mm_per_h + SIN(EXTRACT(EPOCH FROM NOW()) / 45.0 + tank_index * 0.7) * 35) > 5,
    updated_at = NOW()
`.replace(/\s+/g, " ").trim();

const hubTickSql = `
UPDATE hub_sim SET
  line_pressure_mpa = 0.78 + SIN(EXTRACT(EPOCH FROM NOW()) / 70.0) * 0.06,
  line_flow_m3h = 1180 + SIN(EXTRACT(EPOCH FROM NOW()) / 50.0) * 80,
  line_temp_c = 11 + SIN(EXTRACT(EPOCH FROM NOW()) / 90.0) * 2.5,
  delta_pressure_mpa = 0.03 + ABS(SIN(EXTRACT(EPOCH FROM NOW()) / 35.0)) * 0.025,
  total_volume_m3 = 81000 + SIN(EXTRACT(EPOCH FROM NOW()) / 120.0) * 1200,
  updated_at = NOW()
WHERE id = 1
`.replace(/\s+/g, " ").trim();

const objects = [
  {
    parentPath: "root.platform.devices",
    name: "tank-farm-demo",
    type: "CUSTOM",
    displayName: "Резервуарный парк (демо)",
    description: "Marketplace tank-farm folder",
  },
  {
    parentPath: "root.platform.devices.tank-farm-demo",
    name: "hub",
    type: "DEVICE",
    displayName: "Manifold hub",
    templateId: "tank-farm-hub-v1",
  },
  ...TANKS.map((t) => ({
    parentPath: "root.platform.devices.tank-farm-demo",
    name: `tank-${t.n}`,
    type: "DEVICE",
    displayName: `РВС-${t.n}`,
    templateId: "tank-farm-tank-v1",
  })),
];

const bindings = [
  ...TANKS.flatMap((t) => [
    {
      objectPath: `root.platform.devices.tank-farm-demo.tank-${t.n}`,
      variable: "fillLevelMm",
      query: `SELECT level_mm AS value, 'mm' AS unit FROM tank_sim WHERE tank_code = 'tank-${t.n}'`,
      refreshIntervalMs: 2000,
      enabled: true,
    },
    {
      objectPath: `root.platform.devices.tank-farm-demo.tank-${t.n}`,
      variable: "rateMmPerHour",
      query: `SELECT rate_mm_per_h AS value, 'mm/h' AS unit FROM tank_sim WHERE tank_code = 'tank-${t.n}'`,
      refreshIntervalMs: 2000,
      enabled: true,
    },
    {
      objectPath: `root.platform.devices.tank-farm-demo.tank-${t.n}`,
      variable: "valveOpen",
      query: `SELECT valve_open AS value FROM tank_sim WHERE tank_code = 'tank-${t.n}'`,
      refreshIntervalMs: 2000,
      enabled: true,
    },
  ]),
  {
    objectPath: "root.platform.devices.tank-farm-demo.hub",
    variable: "linePressureMpa",
    query: "SELECT line_pressure_mpa AS value, 'MPa' AS unit FROM hub_sim WHERE id = 1",
    refreshIntervalMs: 2000,
    enabled: true,
  },
  {
    objectPath: "root.platform.devices.tank-farm-demo.hub",
    variable: "lineFlowM3h",
    query: "SELECT line_flow_m3h AS value, 'm³/h' AS unit FROM hub_sim WHERE id = 1",
    refreshIntervalMs: 2000,
    enabled: true,
  },
  {
    objectPath: "root.platform.devices.tank-farm-demo.hub",
    variable: "lineTempC",
    query: "SELECT line_temp_c AS value, '°C' AS unit FROM hub_sim WHERE id = 1",
    refreshIntervalMs: 2000,
    enabled: true,
  },
];

const bundle = {
  version: "1.1.0",
  displayName: "Резервуарный парк (демо)",
  tablePrefix: "",
  schemaName: "app_tank_farm_demo",
  metadata: {
    domain: "tank-farm",
    changelog:
      "Marketplace self-contained tank-farm: models, devices, SQL simulation tick (no platform fixture seed).",
  },
  migrations: [
    {
      id: "tank_farm_sim",
      sql: `
CREATE TABLE IF NOT EXISTS tank_sim (
  tank_code VARCHAR(32) PRIMARY KEY,
  tank_index INTEGER NOT NULL DEFAULT 1,
  level_mm DOUBLE PRECISION NOT NULL DEFAULT 5000,
  rate_mm_per_h DOUBLE PRECISION NOT NULL DEFAULT 0,
  rate_bias_mm_per_h DOUBLE PRECISION NOT NULL DEFAULT 0,
  max_level_mm DOUBLE PRECISION NOT NULL DEFAULT 10000,
  valve_open BOOLEAN NOT NULL DEFAULT false,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS hub_sim (
  id INTEGER PRIMARY KEY,
  line_pressure_mpa DOUBLE PRECISION NOT NULL DEFAULT 0.82,
  line_flow_m3h DOUBLE PRECISION NOT NULL DEFAULT 1180,
  line_temp_c DOUBLE PRECISION NOT NULL DEFAULT 11,
  delta_pressure_mpa DOUBLE PRECISION NOT NULL DEFAULT 0.03,
  total_volume_m3 DOUBLE PRECISION NOT NULL DEFAULT 81000,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO hub_sim (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM hub_sim WHERE id = 1);
${TANKS.map(
  (t, i) =>
    `INSERT INTO tank_sim (tank_code, tank_index, level_mm, rate_mm_per_h, rate_bias_mm_per_h, max_level_mm) SELECT 'tank-${t.n}', ${i + 1}, ${t.level}, ${t.rate}, ${t.rate}, ${t.n >= 18 ? 14000 : 10000} WHERE NOT EXISTS (SELECT 1 FROM tank_sim WHERE tank_code = 'tank-${t.n}');`
).join(" ")}
`.replace(/\s+/g, " ").trim(),
    },
  ],
  blueprints: [
    {
      name: "tank-farm-tank-v1",
      description: "Storage tank (marketplace)",
      type: "INSTANCE",
      targetObjectType: "DEVICE",
      suitabilityExpression: "",
      variables: [
        meas("fillLevelMm", "Fill level", "telemetry", "mm", 5000),
        meas("rateMmPerHour", "Level change rate", "telemetry", "mm/h", 0),
        meas("maxLevelMm", "Max level", "config", "mm", 10000),
        boolVar("valveOpen", "Outlet valve open", "status", false),
        ...driverVars(),
      ],
      events: [],
      functions: [],
      bindings: [],
      parameters: {},
    },
    {
      name: "tank-farm-hub-v1",
      description: "Manifold hub (marketplace)",
      type: "INSTANCE",
      targetObjectType: "DEVICE",
      suitabilityExpression: "",
      variables: [
        meas("linePressureMpa", "Line pressure", "telemetry", "MPa", 0.82),
        meas("lineFlowM3h", "Line flow", "telemetry", "m³/h", 1180),
        meas("lineTempC", "Line temperature", "telemetry", "°C", 11),
        meas("deltaPressureMpa", "Delta pressure", "telemetry", "MPa", 0.03),
        meas("totalVolumeM3", "Total volume", "telemetry", "m³", 81000),
        ...driverVars(),
      ],
      events: [],
      functions: [],
      bindings: [],
      parameters: {},
    },
  ],
  objects,
  functions: [
    {
      objectPath: "root.platform.devices.tank-farm-demo.hub",
      functionName: "tank_farm_tick",
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
            { type: "exec", sql: hubTickSql },
            {
              type: "return",
              fields: { error_code: "OK", error_message: "" },
            },
          ],
        }),
      },
    },
  ],
  bindings,
  schedules: [
    {
      scheduleId: "tank-farm-sim-tick",
      enabled: true,
      intervalMs: 5000,
      actionType: "invoke_function",
      action: {
        objectPath: "root.platform.devices.tank-farm-demo.hub",
        functionName: "tank_farm_tick",
      },
    },
  ],
  dashboards: [
    {
      path: "root.platform.dashboards.tank-farm-overview",
      title: "Резервуарный парк",
      refreshIntervalMs: 3000,
      layoutJson: JSON.stringify({
        columns: 84,
        rowHeight: 8,
        widgets: [
          {
            id: "t11",
            type: "value",
            title: "РВС-11 уровень",
            x: 0,
            y: 0,
            w: 21,
            h: 14,
            objectPath: "root.platform.devices.tank-farm-demo.tank-11",
            variableName: "fillLevelMm",
            decimals: 0,
          },
          {
            id: "t12",
            type: "value",
            title: "РВС-12 уровень",
            x: 21,
            y: 0,
            w: 21,
            h: 14,
            objectPath: "root.platform.devices.tank-farm-demo.tank-12",
            variableName: "fillLevelMm",
            decimals: 0,
          },
          {
            id: "hub-p",
            type: "value",
            title: "Давление линии",
            x: 42,
            y: 0,
            w: 21,
            h: 14,
            objectPath: "root.platform.devices.tank-farm-demo.hub",
            variableName: "linePressureMpa",
            decimals: 3,
          },
          {
            id: "hub-f",
            type: "value",
            title: "Расход линии",
            x: 63,
            y: 0,
            w: 21,
            h: 14,
            objectPath: "root.platform.devices.tank-farm-demo.hub",
            variableName: "lineFlowM3h",
            decimals: 1,
          },
        ],
      }),
    },
  ],
  operatorUi: {
    appId: "tank-farm-demo",
    title: "Резервуарный парк",
    defaultDashboard: "root.platform.dashboards.tank-farm-overview",
    dashboards: ["root.platform.dashboards.tank-farm-overview"],
    eventJournalObjectPath: "root.platform",
  },
};

writeFileSync(join(__dirname, "bundle.json"), JSON.stringify(bundle, null, 2) + "\n");
console.log("Wrote examples/tank-farm/bundle.json");
