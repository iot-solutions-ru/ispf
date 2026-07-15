import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  siteId,
  siteLabel,
  snmpHost,
  sections,
  buildDeviceCatalog,
} from "./devices-catalog.mjs";

const siteDir = path.dirname(fileURLToPath(import.meta.url));

function strVar(name, group = "telemetry", writable = false, defaultVal = "") {
  return {
    name,
    group,
    schema: { name: "stringValue", fields: [{ name: "value", type: "STRING" }] },
    readable: true,
    writable,
    defaultValue: {
      schema: { name: "stringValue", fields: [{ name: "value", type: "STRING" }] },
      rows: [{ value: defaultVal }],
    },
  };
}

function intVar(name, group = "telemetry", writable = false, defaultVal = 0, history = false) {
  return {
    name,
    group,
    schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
    readable: true,
    writable,
    ...(history
      ? {
          historyEnabled: true,
          historySampleMode: "ALL_VALUES",
          historyRetentionDays: 7,
        }
      : {}),
    defaultValue: {
      schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
      rows: [{ value: defaultVal }],
    },
  };
}

const networkBlueprint = {
  name: "itm-network-device-v1",
  description: "SNMP-monitored IT infrastructure device (MIB-II, IF-MIB, HOST-RESOURCES)",
  type: "RELATIVE",
  targetObjectType: "DEVICE",
  variables: [
    strVar("hostLabel", "config", true, "device"),
    strVar("topologyElementId", "config", true, ""),
    strVar("snmpHost", "config", true, snmpHost),
    strVar("deviceRole", "config", true, "switch"),
    strVar("sysName", "telemetry"),
    strVar("sysDescr", "telemetry"),
    strVar("sysUpTime", "telemetry"),
    strVar("sysLocation", "telemetry"),
    strVar("sysContact", "telemetry"),
    intVar("ifNumber"),
    strVar("ifDescr"),
    intVar("ifSpeed"),
    intVar("ifOperStatus", "telemetry", false, 0, true),
    intVar("ifInOctets", "telemetry", false, 0, true),
    intVar("ifOutOctets", "telemetry", false, 0, true),
    intVar("ifInErrors"),
    intVar("ifOutErrors"),
    intVar("ifInUcastPkts"),
    intVar("ifOutUcastPkts"),
    intVar("hrMemorySize", "telemetry", false, 0, true),
    intVar("hrSystemProcesses"),
    intVar("hrSystemNumUsers"),
    intVar("hrProcessorLoad", "telemetry", false, 0, true),
    intVar("linkStatus", "status", true, 1),
    intVar("trunkStatus", "status", true, 1),
    {
      name: "status",
      group: "status",
      schema: {
        name: "deviceStatus",
        fields: [
          { name: "online", type: "BOOLEAN" },
          { name: "lastseen", type: "STRING" },
        ],
      },
      readable: true,
      writable: true,
      defaultValue: {
        schema: {
          name: "deviceStatus",
          fields: [
            { name: "online", type: "BOOLEAN" },
            { name: "lastseen", type: "STRING" },
          ],
        },
        rows: [{ online: true, lastseen: "" }],
      },
    },
    intVar("activeAlarms", "status", true, 0),
  ],
};

const deviceCatalog = buildDeviceCatalog();

const objects = [
  {
    parentPath: "root.platform.devices.itm.sites",
    name: siteId,
    type: "CUSTOM",
    displayName: siteLabel,
  },
  {
    parentPath: `root.platform.devices.itm.sites.${siteId}`,
    name: "network",
    type: "CUSTOM",
    displayName: "Сеть DCN",
  },
  {
    parentPath: `root.platform.devices.itm.sites.${siteId}`,
    name: "servers",
    type: "CUSTOM",
    displayName: "Серверы",
  },
  {
    parentPath: `root.platform.devices.itm.sites.${siteId}`,
    name: "isp",
    type: "CUSTOM",
    displayName: "Каналы ISP",
  },
  {
    parentPath: `root.platform.devices.itm.sites.${siteId}`,
    name: "voip",
    type: "CUSTOM",
    displayName: "VoIP",
  },
  {
    parentPath: `root.platform.devices.itm.sites.${siteId}`,
    name: "sections",
    type: "CUSTOM",
    displayName: "Участки",
  },
  ...sections.map((s, i) => ({
    parentPath: `root.platform.devices.itm.sites.${siteId}.sections`,
    name: s,
    type: "CUSTOM",
    displayName: `Участок ${i + 1}`,
  })),
  ...deviceCatalog.map((d) => ({
    parentPath: `root.platform.devices.itm.sites.${siteId}.${d.zone}`,
    name: d.id,
    type: "DEVICE",
    displayName: d.name,
    description: d.description ?? `Demo SNMP ${d.role} @ ${snmpHost}`,
    templateId: "itm-network-device-v1",
  })),
];

const bundle = {
  version: "1.0.0",
  displayName: `ITM inventory — ${siteLabel}`,
  tablePrefix: "",
  schemaName: "it_infra_monitoring",
  metadata: {
    packageId: `itm-plugin-inventory-${siteId}`,
    siteId,
    siteLabel,
    dependsOnApp: "it-infra-monitoring",
    deviceCount: deviceCatalog.length,
    snmpHost,
  },
  blueprints: [networkBlueprint],
  objects,
  migrations: [
    {
      id: `itm_inventory_${siteId}_seed`,
      sql: `INSERT INTO itm_sla_daily (site_id, metric_date, availability_pct, packet_loss_pct, mean_rtt_ms) SELECT '${siteId}', CURRENT_DATE, 99.5, 0.1, 2.5 WHERE NOT EXISTS (SELECT 1 FROM itm_sla_daily WHERE site_id = '${siteId}' AND metric_date = CURRENT_DATE);`,
    },
  ],
};

fs.mkdirSync(siteDir, { recursive: true });
fs.writeFileSync(path.join(siteDir, "bundle.json"), JSON.stringify(bundle, null, 2));
console.log(`Wrote inventory bundle (${objects.length} objects, ${deviceCatalog.length} SNMP devices)`);
