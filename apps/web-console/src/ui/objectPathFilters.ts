import type { ObjectType } from "../types";

/** Container nodes a path prefix or parent folder can point at. */
export const FOLDER_OBJECT_TYPES: ObjectType[] = [
  "ROOT",
  "TENANT",
  "PLATFORM",
  "DEVICES",
  "DASHBOARDS",
  "WORKFLOWS",
  "ALERT_RULES",
  "CORRELATORS",
  "APPLICATIONS",
  "DATA_SOURCES",
  "OPERATOR_APPS",
  "SECURITY",
  "USERS",
  "ROLES",
  "REPORTS",
  "QUERIES",
  "ANALYTICS",
  "EVENT_FILTERS",
  "FUNCTIONS",
  "SCHEDULES",
  "BINDINGS",
  "MIGRATIONS",
  "SCREENS",
  "MIMICS",
  "PROCESS_PROGRAMS",
];

/** Instantiate parent: a folder, or a device that hosts child objects. */
export const PARENT_OBJECT_TYPES: ObjectType[] = [...FOLDER_OBJECT_TYPES, "DEVICE"];
