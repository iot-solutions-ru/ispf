export const siteId = "m11";
export const siteLabel = "Трасса М11";
export const snmpHost = "127.0.0.1";

/** @typedef {{ zone: string, id: string, name: string, role: string, svgId?: string, description?: string }} DeviceDef */

/** DCN topology nodes (MIMIC SVG) */
export const topologyNodes = [
  { id: "tp12", name: "ТП12", svgId: "TP12", role: "switch" },
  { id: "tp14", name: "ТП14", svgId: "TP14", role: "switch" },
  { id: "tp16", name: "ТП16", svgId: "TP16", role: "switch" },
  { id: "cpu5", name: "ЦПУ5", svgId: "CPU5", role: "router" },
  { id: "deu19", name: "ДЭУ19", svgId: "DEU19", role: "switch" },
  { id: "deu19tp12", name: "ДЭУ19-ТП12", svgId: "DEU19TP12", role: "switch" },
  { id: "tspu5deu19", name: "ЦПУ5-ДЭУ19", svgId: "TsPU5DEU19", role: "router" },
  { id: "tp16tspu5", name: "ТП16-ЦПУ5", svgId: "TP16TsPU5", role: "switch" },
  { id: "pkadtp16", name: "ПКАД-ТП16", svgId: "PKADTP16", role: "switch" },
];

/** Additional DCN infrastructure */
export const networkExtras = [
  { id: "sw-dcn-core-01", name: "Core SW #1", role: "switch", description: "Cisco Catalyst 9300 demo" },
  { id: "sw-dcn-core-02", name: "Core SW #2", role: "switch", description: "Cisco Catalyst 9300 demo" },
  { id: "sw-dcn-access-01", name: "Access SW #1", role: "switch", description: "Huawei S5735 demo" },
  { id: "sw-dcn-access-02", name: "Access SW #2", role: "switch", description: "Huawei S5735 demo" },
  { id: "fw-dcn-01", name: "FW DCN #1", role: "firewall", description: "FortiGate demo" },
  { id: "fw-dcn-02", name: "FW DCN #2", role: "firewall", description: "FortiGate demo" },
  { id: "rtr-m11-north", name: "Router North", role: "router", description: "Juniper MX demo" },
  { id: "rtr-m11-south", name: "Router South", role: "router", description: "Juniper MX demo" },
  { id: "lb-dcn-01", name: "Load Balancer", role: "router", description: "F5 BIG-IP demo" },
  { id: "ntp-dcn-01", name: "NTP", role: "server", description: "NTP server SNMP" },
  { id: "dns-dcn-01", name: "DNS", role: "server", description: "BIND server SNMP" },
  { id: "probe-netflow-01", name: "NetFlow Probe", role: "router", description: "sFlow/NetFlow collector" },
  { id: "sw-mgmt-01", name: "Mgmt SW", role: "switch", description: "Out-of-band management switch" },
  { id: "ups-dcn-01", name: "UPS DCN", role: "ups", description: "APC Smart-UPS SNMP card" },
];

export const servers = [
  { id: "nms-01", name: "NMS / ISPF", role: "server", description: "Monitoring platform host" },
  { id: "app-mon-01", name: "App Monitor", role: "server", description: "Application monitoring" },
  { id: "app-report-01", name: "Report Server", role: "server", description: "Reporting services" },
  { id: "db-primary", name: "DB Primary", role: "server", description: "PostgreSQL primary" },
  { id: "db-replica", name: "DB Replica", role: "server", description: "PostgreSQL replica" },
  { id: "backup-01", name: "Backup Server", role: "server", description: "Veeam backup host" },
  { id: "log-01", name: "Log Collector", role: "server", description: "Syslog/ELK node" },
  { id: "es-01", name: "Elasticsearch", role: "server", description: "Search/analytics node" },
  { id: "vm-hypervisor-01", name: "VM Host #1", role: "server", description: "KVM hypervisor" },
  { id: "vm-hypervisor-02", name: "VM Host #2", role: "server", description: "KVM hypervisor" },
  { id: "storage-01", name: "Storage", role: "server", description: "SAN/NAS management" },
  { id: "radius-01", name: "RADIUS", role: "server", description: "802.1X authentication" },
];

export const ispLinks = [
  { id: "isp-primary", name: "ISP Primary", role: "isp", description: "Main WAN channel" },
  { id: "isp-backup", name: "ISP Backup", role: "isp", description: "Backup WAN channel" },
  { id: "isp-rostelecom", name: "Ростелеком", role: "isp", description: "Provider A uplink" },
  { id: "isp-rtk", name: "РТК", role: "isp", description: "Provider B uplink" },
  { id: "isp-megafon", name: "Мегафон LTE", role: "isp", description: "LTE backup channel" },
  { id: "cpe-isp-edge", name: "CPE Edge", role: "router", description: "ISP edge router" },
  { id: "modem-lte-backup", name: "LTE Modem", role: "isp", description: "4G backup modem" },
];

export const voipDevices = [
  { id: "avaya-gw", name: "Avaya GW", role: "voip", description: "Media gateway" },
  { id: "avaya-cm", name: "Avaya CM", role: "voip", description: "Communication Manager" },
  { id: "sbc-primary", name: "SBC Primary", role: "voip", description: "Session border controller" },
  { id: "sbc-backup", name: "SBC Backup", role: "voip", description: "Session border controller" },
  { id: "mgw-01", name: "MGW #1", role: "voip", description: "Media gateway trunk" },
  { id: "mgw-02", name: "MGW #2", role: "voip", description: "Media gateway trunk" },
  { id: "trunk-moscow", name: "Trunk Moscow", role: "voip", description: "SIP trunk to Moscow" },
  { id: "trunk-spb", name: "Trunk SPb", role: "voip", description: "SIP trunk to SPb" },
  { id: "trunk-noc", name: "Trunk NOC", role: "voip", description: "NOC hotline trunk" },
];

export const sections = ["section1", "section2", "section3", "section4", "section5", "section6"];

/** Per-section roadside / toll infrastructure */
export function sectionDevices(section, index) {
  const n = index + 1;
  return [
    { id: `sw-${section}`, name: `SW участок ${n}`, role: "switch", description: "Section access switch" },
    { id: `ap-${section}`, name: `WiFi AP ${n}`, role: "ap", description: "Section WiFi access point" },
    { id: `nvr-${section}`, name: `NVR ${n}`, role: "server", description: "Video surveillance NVR" },
    { id: `plc-${section}`, name: `PLC ${n}`, role: "plc", description: "Roadside PLC controller" },
    { id: `vms-${section}`, name: `Табло ${n}`, role: "vms", description: "Variable message sign" },
    { id: `toll-${section}`, name: `ТСПУ ${n}`, role: "server", description: "Toll collection unit" },
    { id: `ups-${section}`, name: `UPS ${n}`, role: "ups", description: "Section UPS SNMP" },
    { id: `weather-${section}`, name: `Метео ${n}`, role: "weather", description: "Weather station" },
  ];
}

/** @returns {DeviceDef[]} */
export function buildDeviceCatalog() {
  const catalog = [];

  for (const d of topologyNodes) {
    catalog.push({ zone: "network", ...d, description: `DCN node ${d.name}` });
  }
  for (const d of networkExtras) {
    catalog.push({ zone: "network", ...d });
  }
  for (const d of servers) {
    catalog.push({ zone: "servers", ...d });
  }
  for (const d of ispLinks) {
    catalog.push({ zone: "isp", ...d });
  }
  for (const d of voipDevices) {
    catalog.push({ zone: "voip", ...d });
  }
  sections.forEach((section, i) => {
    for (const d of sectionDevices(section, i)) {
      catalog.push({ zone: `sections.${section}`, ...d });
    }
  });

  return catalog;
}

export function devicePath(device) {
  return `root.platform.devices.itm.sites.${siteId}.${device.zone}.${device.id}`;
}

const TOPOLOGY_POLL_IDS = new Set(topologyNodes.map((d) => d.id));

/** Devices that need live SNMP polling for DCN mimic and key dashboards (~20). */
export function isTopologyPollDevice(device) {
  return TOPOLOGY_POLL_IDS.has(device.id);
}
