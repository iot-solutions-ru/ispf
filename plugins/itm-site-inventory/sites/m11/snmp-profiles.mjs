/** SNMP point mappings aligned with deploy/snmpd-ispf.conf and DemoFixtureBootstrap.SNMP_POINT_MAPPINGS */

export const SNMP_CONFIG = {
  host: "127.0.0.1",
  port: "161",
  community: "public",
  version: "2c",
  timeoutMs: "3000",
  retries: "1",
};

const BASE = {
  sysName: "1.3.6.1.2.1.1.5.0:STRING",
  sysDescr: "1.3.6.1.2.1.1.1.0:STRING",
  sysUpTime: "1.3.6.1.2.1.1.3.0",
  sysLocation: "1.3.6.1.2.1.1.6.0:STRING",
  sysContact: "1.3.6.1.2.1.1.4.0:STRING",
};

const IF_MAIN = {
  ifNumber: "1.3.6.1.2.1.2.1.0:INTEGER",
  ifDescr: "1.3.6.1.2.1.2.2.1.2.2:STRING",
  ifSpeed: "1.3.6.1.2.1.2.2.1.5.2:INTEGER",
  ifOperStatus: "1.3.6.1.2.1.2.2.1.8.2:INTEGER",
  ifInOctets: "1.3.6.1.2.1.2.2.1.10.2:INTEGER",
  ifOutOctets: "1.3.6.1.2.1.2.2.1.16.2:INTEGER",
  ifInErrors: "1.3.6.1.2.1.2.2.1.14.2:INTEGER",
  ifOutErrors: "1.3.6.1.2.1.2.2.1.20.2:INTEGER",
  ifInUcastPkts: "1.3.6.1.2.1.2.2.1.11.2:INTEGER",
  ifOutUcastPkts: "1.3.6.1.2.1.2.2.1.17.2:INTEGER",
};

const HOST_RESOURCES = {
  hrMemorySize: "1.3.6.1.2.1.25.2.2.0:INTEGER",
  hrSystemProcesses: "1.3.6.1.2.1.25.1.6.0:INTEGER",
  hrSystemNumUsers: "1.3.6.1.2.1.25.1.5.0:INTEGER",
  hrProcessorLoad: "1.3.6.1.2.1.25.3.3.1.2.196608:INTEGER:optional",
};

/** L2/L3 switch, router, firewall, AP, PLC, VMS — IF-MIB on uplink ifIndex 2 (Linux snmpd) */
export const SNMP_PROFILES = {
  switch: { ...BASE, ...IF_MAIN },
  router: { ...BASE, ...IF_MAIN },
  firewall: { ...BASE, ...IF_MAIN },
  ap: { ...BASE, ...IF_MAIN },
  plc: { ...BASE, ifNumber: IF_MAIN.ifNumber, ifOperStatus: IF_MAIN.ifOperStatus },
  vms: { ...BASE, ...IF_MAIN },
  ups: { ...BASE, ifNumber: IF_MAIN.ifNumber, ifOperStatus: IF_MAIN.ifOperStatus },
  weather: { ...BASE, ifNumber: IF_MAIN.ifNumber },

  /** Server / NVR / toll backend — full HOST-RESOURCES + traffic counters */
  server: { ...BASE, ...IF_MAIN, ...HOST_RESOURCES },

  /** ISP WAN link — linkStatus from ifOperStatus (1=up, 2=down) for itm-isp-links report */
  isp: {
    ...BASE,
    ifNumber: IF_MAIN.ifNumber,
    ifOperStatus: IF_MAIN.ifOperStatus,
    ifInOctets: IF_MAIN.ifInOctets,
    ifOutOctets: IF_MAIN.ifOutOctets,
    linkStatus: IF_MAIN.ifOperStatus,
  },

  /** VoIP trunk/gateway — trunkStatus from ifOperStatus for itm-voip report */
  voip: {
    ...BASE,
    ifOperStatus: IF_MAIN.ifOperStatus,
    ifInOctets: IF_MAIN.ifInOctets,
    ifOutOctets: IF_MAIN.ifOutOctets,
    ifInUcastPkts: IF_MAIN.ifInUcastPkts,
    ifOutUcastPkts: IF_MAIN.ifOutUcastPkts,
    trunkStatus: IF_MAIN.ifOperStatus,
  },
};

export function mappingsForRole(role, { lite = true } = {}) {
  if (!lite) {
    return SNMP_PROFILES[role] ?? SNMP_PROFILES.switch;
  }
  return SNMP_PROFILES_LITE[role] ?? SNMP_PROFILES_LITE.switch;
}

/** Minimal OID set for demo pilot — one UDP round-trip per poll (batch GET).
 *  Host-resources CPU/RAM included for network/server roles so device cards show real values
 *  from local snmpd (HOST-RESOURCES-MIB), not just IF-MIB counters. */
export const SNMP_PROFILES_LITE = {
  switch: {
    sysName: BASE.sysName,
    sysUpTime: BASE.sysUpTime,
    ifOperStatus: IF_MAIN.ifOperStatus,
    ifInOctets: IF_MAIN.ifInOctets,
    ifOutOctets: IF_MAIN.ifOutOctets,
    hrProcessorLoad: HOST_RESOURCES.hrProcessorLoad,
    hrMemorySize: HOST_RESOURCES.hrMemorySize,
  },
  router: {
    sysName: BASE.sysName,
    sysUpTime: BASE.sysUpTime,
    ifOperStatus: IF_MAIN.ifOperStatus,
    ifInOctets: IF_MAIN.ifInOctets,
    ifOutOctets: IF_MAIN.ifOutOctets,
    hrProcessorLoad: HOST_RESOURCES.hrProcessorLoad,
    hrMemorySize: HOST_RESOURCES.hrMemorySize,
  },
  firewall: {
    sysName: BASE.sysName,
    sysUpTime: BASE.sysUpTime,
    ifOperStatus: IF_MAIN.ifOperStatus,
    hrProcessorLoad: HOST_RESOURCES.hrProcessorLoad,
    hrMemorySize: HOST_RESOURCES.hrMemorySize,
  },
  ap: {
    sysName: BASE.sysName,
    ifOperStatus: IF_MAIN.ifOperStatus,
  },
  plc: {
    sysName: BASE.sysName,
    ifOperStatus: IF_MAIN.ifOperStatus,
  },
  vms: {
    sysName: BASE.sysName,
    ifOperStatus: IF_MAIN.ifOperStatus,
    ifInOctets: IF_MAIN.ifInOctets,
    ifOutOctets: IF_MAIN.ifOutOctets,
    hrProcessorLoad: HOST_RESOURCES.hrProcessorLoad,
    hrMemorySize: HOST_RESOURCES.hrMemorySize,
  },
  ups: {
    sysName: BASE.sysName,
    ifOperStatus: IF_MAIN.ifOperStatus,
  },
  weather: {
    sysName: BASE.sysName,
    ifOperStatus: IF_MAIN.ifOperStatus,
  },
  server: {
    sysName: BASE.sysName,
    sysUpTime: BASE.sysUpTime,
    ifOperStatus: IF_MAIN.ifOperStatus,
    ifInOctets: IF_MAIN.ifInOctets,
    ifOutOctets: IF_MAIN.ifOutOctets,
    hrProcessorLoad: HOST_RESOURCES.hrProcessorLoad,
    hrMemorySize: HOST_RESOURCES.hrMemorySize,
  },
  isp: {
    sysName: BASE.sysName,
    ifOperStatus: IF_MAIN.ifOperStatus,
    linkStatus: IF_MAIN.ifOperStatus,
  },
  voip: {
    sysName: BASE.sysName,
    ifOperStatus: IF_MAIN.ifOperStatus,
    trunkStatus: IF_MAIN.ifOperStatus,
  },
};
