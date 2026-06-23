/** Object types that typically contain child nodes in the platform tree. */
const CONTAINER_TYPES = new Set([
  "ROOT",
  "PLATFORM",
  "TENANT",
  "DEVICES",
  "DEVICE",
  "DASHBOARDS",
  "DASHBOARD",
  "WORKFLOWS",
  "WORKFLOW",
  "MODEL",
  "REPORTS",
  "REPORT",
  "APPLICATIONS",
  "APPLICATION",
  "OPERATOR_APPS",
  "OPERATOR_APP",
  "ALERT_RULES",
  "CORRELATORS",
  "FUNCTIONS",
  "SCHEDULES",
  "BINDINGS",
  "MIGRATIONS",
  "SCREENS",
  "DATA_SOURCES",
  "USERS",
  "ROLES",
  "SECURITY",
  "AGENT",
  "FOLDER",
  "CUSTOM",
  "VISUAL_GROUP",
]);

export function isTreeContainerType(type: string): boolean {
  return CONTAINER_TYPES.has(type) || type.endsWith("S");
}
