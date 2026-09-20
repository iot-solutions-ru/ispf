// Extracted from ObjectTreeIcon.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import type { ObjectType } from "../../types";

export type TreeIconKind =
  | "root"
  | "tenant"
  | "user"
  | "device"
  | "driver"
  | "blueprint"
  | "dashboard"
  | "mimic"
  | "workflow"
  | "alert"
  | "agent"
  | "application"
  | "report"
  | "folder"
  | "security"
  | "users-folder"
  | "roles-folder"
  | "functions"
  | "schedules"
  | "bindings"
  | "screens"
  | "star"
  | "gear"
  | "database"
  | "cloud"
  | "tag"
  | "bookmark"
  | "layers"
  | "box"
  | "queries"
  | "analytics"
  | "filter"
  | "mes"
  | "work-orders"
  | "quality"
  | "custom";

export type TreeIconCategory = "general" | "platform" | "objects" | "application";

export interface TreeIconDefinition {
  id: TreeIconKind;
  category: TreeIconCategory;
}

export const TREE_ICON_CATALOG: TreeIconDefinition[] = [
  { id: "folder", category: "general" },
  { id: "box", category: "general" },
  { id: "layers", category: "general" },
  { id: "star", category: "general" },
  { id: "tag", category: "general" },
  { id: "bookmark", category: "general" },
  { id: "gear", category: "general" },
  { id: "database", category: "general" },
  { id: "cloud", category: "general" },
  { id: "root", category: "platform" },
  { id: "tenant", category: "platform" },
  { id: "security", category: "platform" },
  { id: "users-folder", category: "platform" },
  { id: "roles-folder", category: "platform" },
  { id: "device", category: "objects" },
  { id: "driver", category: "objects" },
  { id: "blueprint", category: "objects" },
  { id: "dashboard", category: "objects" },
  { id: "mimic", category: "objects" },
  { id: "workflow", category: "objects" },
  { id: "application", category: "objects" },
  { id: "report", category: "objects" },
  { id: "user", category: "objects" },
  { id: "agent", category: "objects" },
  { id: "alert", category: "objects" },
  { id: "functions", category: "application" },
  { id: "schedules", category: "application" },
  { id: "bindings", category: "application" },
  { id: "screens", category: "application" },
  { id: "queries", category: "application" },
  { id: "analytics", category: "application" },
  { id: "filter", category: "application" },
  { id: "mes", category: "platform" },
  { id: "work-orders", category: "platform" },
  { id: "quality", category: "platform" },
  { id: "custom", category: "general" },
];

const ICON_IDS = new Set<string>(TREE_ICON_CATALOG.map((item) => item.id));

/** Legacy uiIcon values persisted before Model → Blueprint rename. */
const LEGACY_ICON_ALIASES: Record<string, TreeIconKind> = {
  model: "blueprint",
};

function isTreeIconId(value: string | null | undefined): value is TreeIconKind {
  return Boolean(value && ICON_IDS.has(value));
}

export function normalizeIconId(value: string | null | undefined): TreeIconKind | null {
  if (!value) {
    return null;
  }
  const aliased = LEGACY_ICON_ALIASES[value];
  if (aliased) {
    return aliased;
  }
  return isTreeIconId(value) ? value : null;
}

export function resolveTreeIconKind(path: string, type: ObjectType): TreeIconKind {
  const leaf = path.split(".").pop() ?? "";

  if (path === "root.platform.security" || leaf === "security") {
    return "security";
  }
  if (path.endsWith(".security.users") || leaf === "users") {
    return "users-folder";
  }
  if (path.endsWith(".security.roles") || leaf === "roles") {
    return "roles-folder";
  }
  if (path.includes(".functions")) {
    return "functions";
  }
  if (path.includes(".schedules")) {
    return "schedules";
  }
  if (path.includes(".bindings")) {
    return "bindings";
  }
  if (path.includes(".data-sources")) {
    return "database";
  }
  if (path.includes(".screens")) {
    return "screens";
  }
  if (path.includes(".operator-apps.") && !path.endsWith(".operator-apps")) {
    return "application";
  }
  if (path.endsWith(".operator-apps")) {
    return "screens";
  }
  if (path.includes(".reports")) {
    return "report";
  }
  if (path.includes(".queries")) {
    return "queries";
  }
  if (path.includes(".analytics")) {
    return "analytics";
  }
  if (path.includes(".event-filters")) {
    return "filter";
  }
  if (path === "root.platform.mes" || path.startsWith("root.platform.mes.")) {
    if (path.includes(".work-orders")) {
      return "work-orders";
    }
    if (path.includes(".operations")) {
      return "gear";
    }
    if (path.includes(".lots")) {
      return "box";
    }
    if (path.includes(".shifts")) {
      return "schedules";
    }
    if (path.includes(".quality-records")) {
      return "quality";
    }
    if (path.includes(".instances")) {
      return "layers";
    }
    return "mes";
  }
  if (path.endsWith(".mimics") || path.includes(".mimics.")) {
    return "mimic";
  }
  if (
    path.endsWith(".mixin-blueprints")
    || path.endsWith(".singleton-blueprints")
    || path.endsWith(".instance-types")
    || path.includes(".mixin-blueprints.")
    || path.includes(".singleton-blueprints.")
    || path.includes(".instance-types.")
  ) {
    return "blueprint";
  }

  switch (type) {
    case "ROOT":
      return "root";
    case "TENANT":
    case "PLATFORM":
      return "tenant";
    case "USER":
      return "user";
    case "DEVICES":
    case "DEVICE":
      return "device";
    case "DRIVER":
      return "driver";
    case "BLUEPRINT":
      return "blueprint";
    case "DASHBOARDS":
    case "DASHBOARD":
      return "dashboard";
    case "MIMICS":
    case "MIMIC":
      return "mimic";
    case "WORKFLOWS":
    case "WORKFLOW":
    case "CORRELATORS":
    case "CORRELATOR":
    case "PROCESS_PROGRAMS":
    case "PROCESS_PROGRAM":
      return "workflow";
    case "ALERT_RULES":
    case "ALERT":
      return "alert";
    case "APPLICATIONS":
    case "APPLICATION":
      return "application";
    case "OPERATOR_APPS":
      return "screens";
    case "DATA_SOURCES":
    case "DATA_SOURCE":
      return "database";
    case "REPORTS":
    case "REPORT":
      return "report";
    case "SECURITY":
      return "security";
    case "USERS":
      return "users-folder";
    case "ROLES":
    case "ROLE":
      return "roles-folder";
    case "FUNCTIONS":
    case "FUNCTION":
      return "functions";
    case "SCHEDULES":
    case "SCHEDULE":
      return "schedules";
    case "BINDINGS":
    case "BINDING":
      return "bindings";
    case "QUERIES":
      return "queries";
    case "CUSTOM":
      if (path.includes(".queries.")) {
        return "queries";
      }
      return "custom";
    case "ANALYTICS":
    case "ANALYTICS_TEMPLATE":
      return "analytics";
    case "EVENT_FILTERS":
    case "EVENT_FILTER":
      return "filter";
    case "MES":
      return "mes";
    case "WORK_ORDERS":
    case "WORK_ORDER":
      return "work-orders";
    case "OPERATIONS":
    case "OPERATION":
      return "gear";
    case "LOTS":
    case "LOT":
      return "box";
    case "SHIFTS":
    case "SHIFT":
      return "schedules";
    case "QUALITY_RECORDS":
    case "QUALITY_RECORD":
      return "quality";
    case "MES_INSTANCES":
      return "layers";
    case "MIGRATIONS":
    case "MIGRATION":
      return "database";
    case "SCREENS":
    case "SCREEN":
      return "screens";
    case "AGENT":
      return "agent";
    default:
      return "folder";
  }
}
