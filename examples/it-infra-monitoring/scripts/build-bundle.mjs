import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const topologyPluginDir = path.resolve(
  root,
  "../../plugins/itm-site-topology/sites/m11"
);
const topologyConfigPath = path.join(topologyPluginDir, "topology-svg-config.json");

function ensureTopologyConfig() {
  if (fs.existsSync(topologyConfigPath)) return;
  const script = path.join(topologyPluginDir, "build-topology-svg.mjs");
  if (!fs.existsSync(script)) {
    console.warn("topology-svg-config.json missing and build script not found:", script);
    return;
  }
  const res = spawnSync(process.execPath, [script], { stdio: "inherit", cwd: topologyPluginDir });
  if (res.status !== 0) process.exit(res.status ?? 1);
}

ensureTopologyConfig();

function inferBindingType(behavior) {
  if (!behavior) return "string";
  switch (behavior.type) {
    case "fill":
    case "stroke":
    case "visibility":
    case "hidden":
    case "blink":
      return "boolean";
    case "fillLevel":
      return "number";
    case "text":
      return behavior.format === "number" ? "number" : "string";
    default:
      return "string";
  }
}

function behaviorBindingKeys(behaviors) {
  const keys = new Set();
  for (const b of behaviors) {
    keys.add(b.bind);
    if (b.type === "text" && b.qualityBind?.trim()) keys.add(b.qualityBind.trim());
    if (b.type === "fillLevel" && b.maxBind?.trim()) keys.add(b.maxBind.trim());
  }
  return [...keys];
}

function syncBindingSchemaFromBehaviors(behaviors, existing = []) {
  const existingMap = new Map(existing.map((s) => [s.key, s]));
  const primaryBinds = new Set(behaviors.map((b) => b.bind));
  return behaviorBindingKeys(behaviors).map((key) => {
    const prev = existingMap.get(key);
    if (prev) return prev;
    const behavior = behaviors.find((b) => b.bind === key);
    return {
      key,
      labelKey: `bindings.${key}`,
      type: inferBindingType(behavior),
      optional: !primaryBinds.has(key),
    };
  });
}

const layouts = JSON.parse(
  fs.readFileSync(path.join(root, "dashboards/layouts.json"), "utf8")
);

if (fs.existsSync(topologyConfigPath) && layouts["itm-dcn"]) {
  const config = JSON.parse(fs.readFileSync(topologyConfigPath, "utf8"));
  const bindingSchema = syncBindingSchemaFromBehaviors(config.behaviors ?? []);
  for (const widget of layouts["itm-dcn"].widgets) {
    if (widget.id === "dcn-topology" && widget.type === "svg-widget" && widget.svgInteractiveInject) {
      widget.behaviorsJson = JSON.stringify(config.behaviors ?? []);
      widget.bindingsJson = JSON.stringify(config.bindings ?? {});
      widget.bindingSchemaJson = JSON.stringify(bindingSchema);
      widget.hitAreasJson = JSON.stringify(config.hitAreas ?? []);
      widget.svgInnerJson = config.svgInner;
      widget.viewBox = config.viewBox;
      widget.backgroundColor = config.backgroundColor;
      delete widget.svgInteractiveInject;
      delete widget.topologyJson;
    }
  }
}

const dashKeys = [
  "itm-overview",
  "itm-top10",
  "itm-alarms",
  "itm-sla",
  "itm-sla-top10",
  "itm-traffic",
  "itm-services-voip",
  "itm-servers",
  "itm-dcn",
  "itm-device-detail",
];

const titles = {
  "itm-overview": "Network Overview",
  "itm-top10": "Top 10",
  "itm-alarms": "Alerts",
  "itm-sla": "IP SLA Summary",
  "itm-sla-top10": "IP SLA Top 10",
  "itm-traffic": "Traffic Summary",
  "itm-services-voip": "VoIP Summary",
  "itm-servers": "Virtualization",
  "itm-dcn": "Network Topology Map",
  "itm-device-detail": "Карточка устройства",
};

const dashboards = dashKeys.map((k) => ({
  path: `root.platform.dashboards.${k}`,
  title: titles[k],
  refreshIntervalMs: 5000,
  layoutJson: JSON.stringify(layouts[k]),
}));

const bundle = {
  version: "1.0.0",
  displayName: "Мониторинг ИТ инфраструктуры",
  tablePrefix: "itm_",
  schemaName: "it_infra_monitoring",
  metadata: {
    description:
      "Универсальное NMS-приложение ISPF (it-infra-monitoring). Объекты площадки подключаются site-плагинами.",
    appId: "it-infra-monitoring",
    treeRoot: "root.platform.devices.itm",
  },
  migrations: [
    {
      id: "itm_schema_v1",
      sql: [
        "CREATE TABLE IF NOT EXISTS itm_sla_daily (",
        "  site_id VARCHAR(64) NOT NULL,",
        "  metric_date DATE NOT NULL,",
        "  availability_pct DOUBLE PRECISION,",
        "  packet_loss_pct DOUBLE PRECISION,",
        "  mean_rtt_ms DOUBLE PRECISION,",
        "  PRIMARY KEY (site_id, metric_date)",
        ");",
        "CREATE TABLE IF NOT EXISTS itm_traffic_top (",
        "  site_id VARCHAR(64) NOT NULL,",
        "  recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),",
        "  src_host VARCHAR(128),",
        "  dst_host VARCHAR(128),",
        "  bytes BIGINT,",
        "  protocol VARCHAR(32)",
        ");",
        "CREATE INDEX IF NOT EXISTS itm_traffic_top_site_time ON itm_traffic_top (site_id, recorded_at DESC);",
      ].join(" "),
    },
  ],
  reports: [
    {
      reportId: "itm-device-status",
      title: "Статус устройств",
      description: "Сводка online/offline по дереву itm",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.*",
      variableName: "status",
      columns: [
        { field: "devicepath", label: "Путь" },
        { field: "online", label: "Online" },
        { field: "lastseen", label: "Последний опрос" },
      ],
      maxRows: 2000,
    },
    {
      reportId: "itm-active-alarms",
      title: "Активные аварии",
      description: "Список активных тревог (узлы offline / линки down). Пусто, если аварий нет.",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.hub",
      variableName: "activeAlarmsFeed",
      columns: [
        { field: "severity", label: "Уровень" },
        { field: "source", label: "Источник" },
        { field: "message", label: "Сообщение" },
        { field: "objectPath", label: "Объект" },
        { field: "ts", label: "Время" },
      ],
      maxRows: 500,
    },
    {
      reportId: "itm-network-devices",
      title: "Сетевые устройства",
      description: "SNMP sysName и uptime",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.sites.*.network.*",
      variableName: "sysName",
      columns: [
        { field: "devicepath", label: "Устройство" },
        { field: "value", label: "sysName" },
      ],
      maxRows: 500,
    },
    {
      reportId: "itm-servers",
      title: "Серверы",
      description: "Мониторинг серверов",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.sites.*.servers.*",
      variableName: "hostLabel",
      columns: [
        { field: "devicepath", label: "Сервер" },
        { field: "value", label: "Имя" },
      ],
      maxRows: 200,
    },
    {
      reportId: "itm-traffic-top",
      title: "Top трафик",
      description: "Агрегат sFlow (SQL)",
      reportType: "sql",
      query:
        "SELECT recorded_at, src_host, dst_host, bytes, protocol FROM itm_traffic_top ORDER BY recorded_at DESC LIMIT 200",
      columns: [
        { field: "recorded_at", label: "Время" },
        { field: "src_host", label: "Источник" },
        { field: "dst_host", label: "Назначение" },
        { field: "bytes", label: "Байт" },
        { field: "protocol", label: "Протокол" },
      ],
      maxRows: 200,
    },
    {
      reportId: "itm-sla-summary",
      title: "SLA сводка",
      description: "Дневные SLA метрики",
      reportType: "sql",
      query:
        "SELECT site_id, metric_date, availability_pct, packet_loss_pct, mean_rtt_ms FROM itm_sla_daily ORDER BY metric_date DESC",
      columns: [
        { field: "site_id", label: "Площадка" },
        { field: "metric_date", label: "Дата" },
        { field: "availability_pct", label: "Доступность %" },
        { field: "packet_loss_pct", label: "Потери %" },
        { field: "mean_rtt_ms", label: "RTT мс" },
      ],
      maxRows: 365,
    },
    {
      reportId: "itm-isp-links",
      title: "Каналы ISP",
      description: "Статус внешних каналов",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.sites.*.isp.*",
      variableName: "linkStatus",
      columns: [
        { field: "devicepath", label: "Канал" },
        { field: "value", label: "Статус" },
      ],
      maxRows: 50,
    },
    {
      reportId: "itm-voip",
      title: "VoIP",
      description: "Avaya / VoIP шлюзы",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.sites.*.voip.*",
      variableName: "trunkStatus",
      columns: [
        { field: "devicepath", label: "Транк" },
        { field: "value", label: "Статус" },
      ],
      maxRows: 100,
    },
    {
      reportId: "itm-network-traffic-top",
      title: "Top DCN by Traffic",
      description: "ifInOctets по сетевым устройствам",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.sites.*.network.*",
      variableName: "ifInOctets",
      columns: [
        { field: "devicepath", label: "Устройство" },
        { field: "value", label: "In Octets" },
      ],
      maxRows: 10,
    },
    {
      reportId: "itm-network-util-top",
      title: "Top DCN by Utilization",
      description: "ifOperStatus / скорость порта по сетевым устройствам",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.sites.*.network.*",
      variableName: "ifOperStatus",
      columns: [
        { field: "devicepath", label: "Устройство" },
        { field: "value", label: "ifOperStatus" },
      ],
      maxRows: 10,
    },
    {
      reportId: "itm-server-cpu-top",
      title: "Top CPU Load",
      description: "hrProcessorLoad по серверам",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.sites.*.servers.*",
      variableName: "hrProcessorLoad",
      columns: [
        { field: "devicepath", label: "Сервер" },
        { field: "value", label: "CPU %" },
      ],
      maxRows: 10,
    },
    {
      reportId: "itm-server-memory",
      title: "Server Memory",
      description: "hrMemorySize по серверам",
      reportType: "tree-variables",
      devicePathPattern: "root.platform.devices.itm.sites.*.servers.*",
      variableName: "hrMemorySize",
      columns: [
        { field: "devicepath", label: "Сервер" },
        { field: "value", label: "Memory KB" },
      ],
      maxRows: 200,
    },
    {
      reportId: "itm-sla-top10",
      title: "IP SLA Top 10",
      description: "Худшие SLA по потерям пакетов",
      reportType: "sql",
      query:
        "SELECT site_id, metric_date, availability_pct, packet_loss_pct, mean_rtt_ms FROM itm_sla_daily ORDER BY packet_loss_pct DESC NULLS LAST LIMIT 10",
      columns: [
        { field: "site_id", label: "Площадка" },
        { field: "metric_date", label: "Дата" },
        { field: "availability_pct", label: "Доступность %" },
        { field: "packet_loss_pct", label: "Потери %" },
        { field: "mean_rtt_ms", label: "RTT мс" },
      ],
      maxRows: 10,
    },
  ],
  events: [
    { id: "itmDeviceDown", roles: ["operator", "admin", "noc"] },
    { id: "itmDeviceDownCleared", roles: ["operator", "admin", "noc"] },
    { id: "itmLinkDown", roles: ["operator", "admin", "noc"] },
    { id: "itmLinkDownCleared", roles: ["operator", "admin", "noc"] },
    { id: "itmSnmpTrap", roles: ["operator", "admin", "noc"] },
    { id: "itmSyslogAlert", roles: ["operator", "admin", "noc"] },
    { id: "itmSlaBreach", roles: ["operator", "admin", "noc"] },
  ],
  blueprints: [
    {
      name: "itm-hub-v1",
      description: "ITM monitoring hub (KPI aggregates, event journal anchor)",
      type: "RELATIVE",
      targetObjectType: "DEVICE",
      variables: [
        {
          name: "siteLabel",
          description: "Active site label for operator UI",
          group: "config",
          schema: { name: "stringValue", fields: [{ name: "value", type: "STRING" }] },
          readable: true,
          writable: true,
          defaultValue: {
            schema: { name: "stringValue", fields: [{ name: "value", type: "STRING" }] },
            rows: [{ value: "—" }],
          },
        },
        {
          name: "kpiAvailability",
          description: "Site availability %",
          group: "kpi",
          schema: { name: "doubleValue", fields: [{ name: "value", type: "DOUBLE" }] },
          readable: true,
          writable: true,
          historyEnabled: true,
          defaultValue: {
            schema: { name: "doubleValue", fields: [{ name: "value", type: "DOUBLE" }] },
            rows: [{ value: 100.0 }],
          },
        },
        {
          name: "kpiActiveAlarms",
          description: "Active alarm count",
          group: "kpi",
          schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
          readable: true,
          writable: true,
          defaultValue: {
            schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
            rows: [{ value: 0 }],
          },
        },
        {
          name: "kpiDevicesUp",
          description: "Devices online",
          group: "kpi",
          schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
          readable: true,
          writable: true,
          defaultValue: {
            schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
            rows: [{ value: 0 }],
          },
        },
        {
          name: "kpiDevicesDown",
          description: "Devices offline",
          group: "kpi",
          schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
          readable: true,
          writable: false,
          defaultValue: {
            schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
            rows: [{ value: 0 }],
          },
        },
        {
          name: "status",
          description: "Connectivity status",
          group: "status",
          schema: {
            name: "deviceStatus",
            fields: [
              { name: "online", type: "BOOLEAN" },
              { name: "lastseen", type: "STRING" },
            ],
          },
          readable: true,
          writable: false,
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
        {
          name: "activeAlarms",
          description: "Hub-level active alarms counter",
          group: "status",
          schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
          readable: true,
          writable: true,
          defaultValue: {
            schema: { name: "intValue", fields: [{ name: "value", type: "INTEGER" }] },
            rows: [{ value: 0 }],
          },
        },
        {
          name: "activeAlarmsFeed",
          description: "Active topology / device alarms as a list (empty when healthy)",
          group: "status",
          schema: {
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
          },
          readable: true,
          writable: true,
          defaultValue: {
            schema: {
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
            },
            rows: [{ items: [] }],
          },
        },
      ],
      events: [
        { name: "itmDeviceDown", description: "Device offline", level: "WARNING" },
        { name: "itmLinkDown", description: "Link down", level: "WARNING" },
        { name: "itmSyslogAlert", description: "Syslog alert", level: "INFO" },
        { name: "itmSnmpTrap", description: "SNMP trap", level: "WARNING" },
      ],
    },
  ],
  objects: [
    {
      parentPath: "root.platform.devices",
      name: "itm",
      type: "CUSTOM",
      displayName: "IT Infrastructure Monitoring",
    },
    {
      parentPath: "root.platform.devices.itm",
      name: "hub",
      type: "DEVICE",
      displayName: "ITM Hub",
      templateId: "itm-hub-v1",
    },
    {
      parentPath: "root.platform.devices.itm",
      name: "ingress",
      type: "CUSTOM",
      displayName: "Ingress collectors",
    },
    {
      parentPath: "root.platform.devices.itm",
      name: "sites",
      type: "CUSTOM",
      displayName: "Sites",
    },
    {
      parentPath: "root.platform.mimics",
      name: "itm-dcn-placeholder",
      type: "MIMIC",
      displayName: "DCN placeholder (replace via site topology plugin)",
    },
  ],
  alertRules: [
    {
      name: "ITM device offline",
      objectPath: "root.platform.devices.itm.hub",
      watchVariable: "kpiDevicesDown",
      conditionExpr: 'self.kpiDevicesDown["value"] > 0',
      eventName: "itmDeviceDown",
      payloadVariable: "kpiDevicesDown",
      enabled: true,
      edgeTrigger: true,
      delaySeconds: 60,
      sustainWhileTrue: false,
    },
  ],
  dashboards,
  operatorUi: {
    appId: "it-infra-monitoring",
    title: "Мониторинг ИТ инфраструктуры",
    defaultDashboard: "root.platform.dashboards.itm-overview",
    eventJournalObjectPath: "root.platform.devices.itm.hub",
    dashboards: dashKeys.map((k) => ({
      path: `root.platform.dashboards.${k}`,
      title: titles[k],
    })),
    reports: [
      { path: "root.platform.reports.itm-device-status", title: "Network Overview" },
      { path: "root.platform.reports.itm-active-alarms", title: "Active Alarms" },
      { path: "root.platform.reports.itm-network-traffic-top", title: "Top DCN by Traffic" },
      { path: "root.platform.reports.itm-network-util-top", title: "Top DCN by Utilization" },
      { path: "root.platform.reports.itm-server-cpu-top", title: "Top CPU Load" },
      { path: "root.platform.reports.itm-server-memory", title: "Server Memory" },
      { path: "root.platform.reports.itm-network-devices", title: "Сетевые устройства" },
      { path: "root.platform.reports.itm-servers", title: "Серверы" },
      { path: "root.platform.reports.itm-traffic-top", title: "Traffic Summary" },
      { path: "root.platform.reports.itm-sla-summary", title: "IP SLA Summary" },
      { path: "root.platform.reports.itm-sla-top10", title: "IP SLA Top 10" },
      { path: "root.platform.reports.itm-isp-links", title: "ISP Links" },
      { path: "root.platform.reports.itm-voip", title: "VoIP Summary" },
    ],
    defaultReport: "root.platform.reports.itm-device-status",
  },
};

fs.writeFileSync(path.join(root, "bundle.json"), JSON.stringify(bundle, null, 2));
console.log(`Wrote bundle.json (${dashboards.length} dashboards)`);
