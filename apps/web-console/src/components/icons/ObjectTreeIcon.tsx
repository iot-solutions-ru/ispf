import type { ReactNode } from "react";
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

export function isTreeIconId(value: string | null | undefined): value is TreeIconKind {
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
    path.endsWith(".relative-blueprints")
    || path.endsWith(".absolute-blueprints")
    || path.endsWith(".instance-types")
    || path.includes(".relative-blueprints.")
    || path.includes(".absolute-blueprints.")
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
    case "CUSTOM":
    default:
      return "folder";
  }
}

export function resolveObjectTreeIcon(
  path: string,
  type: ObjectType,
  iconId?: string | null
): TreeIconKind {
  const normalized = normalizeIconId(iconId);
  if (normalized) {
    return normalized;
  }
  return resolveTreeIconKind(path, type);
}

const ICONS: Record<TreeIconKind, ReactNode> = {
  root: (
    <>
      <circle cx="8" cy="8" r="2.2" fill="currentColor" stroke="none" />
      <path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.4 3.4l1.4 1.4M11.2 11.2l1.4 1.4M3.4 12.6l1.4-1.4M11.2 4.8l1.4-1.4" />
    </>
  ),
  tenant: (
    <>
      <path d="M2.5 13.5V6l5.5-3.5L13.5 6v7.5" />
      <path d="M6 13.5V9.5h4v4" />
    </>
  ),
  user: (
    <>
      <circle cx="8" cy="5.5" r="2.2" />
      <path d="M3.5 13.5c.8-2.6 2.6-4 4.5-4s3.7 1.4 4.5 4" />
    </>
  ),
  device: (
    <>
      <rect x="3" y="2.5" width="10" height="11" rx="1.5" />
      <path d="M6 12.5h4" />
      <circle cx="8" cy="7" r="1.6" fill="currentColor" stroke="none" />
    </>
  ),
  driver: (
    <>
      <path d="M2.5 8h2.5l1.5-3h4l1.5 3H13.5" />
      <circle cx="5" cy="10.5" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="11" cy="10.5" r="1.2" fill="currentColor" stroke="none" />
    </>
  ),
  blueprint: (
    <>
      <rect x="2.5" y="2.5" width="11" height="11" rx="1.2" />
      <path d="M5 5.5h6M5 8h6M5 10.5h4" />
    </>
  ),
  dashboard: (
    <>
      <rect x="2.5" y="2.5" width="5" height="5" rx="0.8" />
      <rect x="8.5" y="2.5" width="5" height="5" rx="0.8" />
      <rect x="2.5" y="8.5" width="11" height="5" rx="0.8" />
    </>
  ),
  mimic: (
    <>
      <rect x="2" y="2.5" width="12" height="11" rx="1" />
      <ellipse cx="5" cy="10" rx="1.5" ry="2" />
      <path d="M6.8 10h2.2" />
      <rect x="9.3" y="8.8" width="2.6" height="2.4" rx="0.4" />
      <path d="M4 5.5h8" />
      <circle cx="8" cy="5.5" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  workflow: (
    <>
      <circle cx="4" cy="8" r="1.6" />
      <circle cx="12" cy="4.5" r="1.6" />
      <circle cx="12" cy="11.5" r="1.6" />
      <path d="M5.5 7.2L10.2 5M5.5 8.8l4.7 2.2" />
    </>
  ),
  alert: (
    <>
      <path d="M8 2.5l5.5 9.5H2.5L8 2.5z" />
      <path d="M8 6.5v2.5" />
      <circle cx="8" cy="11" r="0.7" fill="currentColor" stroke="none" />
    </>
  ),
  agent: (
    <>
      <path d="M8 2.5v2.5M5.5 4.5L8 6M10.5 4.5L8 6" />
      <rect x="4" y="6.5" width="8" height="7" rx="1.5" />
      <path d="M6.5 10h3" />
    </>
  ),
  application: (
    <>
      <rect x="3" y="2.5" width="10" height="11" rx="1.5" />
      <path d="M6 5.5h4M6 8h4M6 10.5h2.5" />
    </>
  ),
  report: (
    <>
      <path d="M4 2.5h8v11H4z" />
      <path d="M6 6h4M6 8.5h4M6 11h2.5" />
      <path d="M10.5 11l1.5 1.5" />
    </>
  ),
  folder: (
    <path d="M2.5 4.5c0-.8.6-1.5 1.5-1.5h2.8l1.2 1.5h4.5c.8 0 1.5.7 1.5 1.5v6.5c0 .8-.7 1.5-1.5 1.5H4c-.8 0-1.5-.7-1.5-1.5V4.5z" />
  ),
  security: (
    <>
      <path d="M8 2.5l4.5 2v4.5c0 2.8-2 4.3-4.5 5-2.5-.7-4.5-2.2-4.5-5V4.5L8 2.5z" />
      <path d="M6.2 8l1.3 1.3 2.8-2.8" />
    </>
  ),
  "users-folder": (
    <>
      <path d="M2.5 4.5c0-.8.6-1.5 1.5-1.5h2.8l1.2 1.5h4.5c.8 0 1.5.7 1.5 1.5v6.5c0 .8-.7 1.5-1.5 1.5H4c-.8 0-1.5-.7-1.5-1.5V4.5z" />
      <circle cx="6.5" cy="9" r="1" fill="currentColor" stroke="none" />
      <circle cx="9.5" cy="9" r="1" fill="currentColor" stroke="none" />
    </>
  ),
  "roles-folder": (
    <>
      <path d="M2.5 4.5c0-.8.6-1.5 1.5-1.5h2.8l1.2 1.5h4.5c.8 0 1.5.7 1.5 1.5v6.5c0 .8-.7 1.5-1.5 1.5H4c-.8 0-1.5-.7-1.5-1.5V4.5z" />
      <path d="M6 9.5h4M8 8v3" />
    </>
  ),
  functions: <path d="M5 3.5h6v2.5H8.5L11 8.5 8.5 11H11v2.5H5V11h2.5L5 8.5 7.5 6H5V3.5z" />,
  schedules: (
    <>
      <circle cx="8" cy="8" r="5" />
      <path d="M8 5v3.5l2.5 1.5" />
    </>
  ),
  bindings: (
    <>
      <path d="M3.5 8h3M9.5 8h3" />
      <circle cx="7" cy="8" r="1.5" />
      <path d="M3.5 4.5h9M3.5 11.5h9" />
    </>
  ),
  screens: (
    <>
      <rect x="2.5" y="3.5" width="11" height="9" rx="1.2" />
      <path d="M5 6.5h6M5 9h4" />
    </>
  ),
  star: (
    <path
      d="M8 2.8l1.4 3h3.2l-2.6 1.9 1 3.2L8 9.8l-3 1.1 1-3.2-2.6-1.9h3.2L8 2.8z"
      fill="currentColor"
      stroke="none"
    />
  ),
  gear: (
    <>
      <circle cx="8" cy="8" r="2.2" />
      <path d="M8 1.8v1.6M8 12.6v1.6M1.8 8h1.6M12.6 8h1.6M3.7 3.7l1.1 1.1M11.2 11.2l1.1 1.1M3.7 12.3l1.1-1.1M11.2 4.8l1.1-1.1" />
    </>
  ),
  database: (
    <>
      <ellipse cx="8" cy="4.5" rx="4.5" ry="1.6" />
      <path d="M3.5 4.5v7c0 .9 2 1.6 4.5 1.6s4.5-.7 4.5-1.6v-7" />
      <path d="M3.5 8c0 .9 2 1.6 4.5 1.6s4.5-.7 4.5-1.6" />
    </>
  ),
  cloud: <path d="M4.5 11.5h7.2a2.5 2.5 0 0 0 .4-5 3.2 3.2 0 0 0-6.1-1.1A2.4 2.4 0 0 0 4.5 11.5z" />,
  tag: (
    <>
      <path d="M3 3.5h4.5l5.5 5.5-4.5 4.5L3 8V3.5z" />
      <circle cx="6" cy="6" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  bookmark: <path d="M4.5 2.5h7v11l-3.5-2.2L4.5 13.5V2.5z" />,
  layers: (
    <>
      <path d="M8 2.5L13.5 5.5 8 8.5 2.5 5.5 8 2.5z" />
      <path d="M2.5 8.5L8 11.5l5.5-3" />
      <path d="M2.5 11l5.5 3 5.5-3" />
    </>
  ),
  box: (
    <>
      <path d="M3 5.5l5-2.5 5 2.5v5l-5 2.5-5-2.5v-5z" />
      <path d="M8 3v10M3 5.5l5 2.5 5-2.5" />
    </>
  ),
  queries: (
    <>
      <circle cx="7" cy="7" r="3.2" />
      <path d="M9.4 9.4L12 12" />
      <path d="M3.5 12.5h4.5" />
    </>
  ),
  analytics: (
    <>
      <path d="M2.5 12V4.5M2.5 12h11" />
      <path d="M4 9.5l2.2-2.2 2.3 1.4 3-3.2" />
    </>
  ),
  filter: <path d="M3 3.5h10l-4 5v3.5l-2 1V8.5L3 3.5z" />,
  mes: (
    <>
      <path d="M2.5 12.5V8l2.5-1.8V9l2-1.5v5" />
      <path d="M9 12.5V7.5l2.5-1.8v6.8" />
      <path d="M2 12.5h12" />
    </>
  ),
  "work-orders": (
    <>
      <path d="M5.5 3h5v1.8H5.5z" />
      <rect x="4.5" y="4.5" width="7" height="9" rx="1" />
      <path d="M6.5 7.5h3M6.5 9.5h3M6.5 11.5h2" />
    </>
  ),
  quality: (
    <>
      <path d="M8 2.5l4 2v4c0 2.4-1.8 3.7-4 4.3-2.2-.6-4-1.9-4-4.3V4.5L8 2.5z" />
      <path d="M6.2 8.2l1.2 1.2 2.6-2.6" />
    </>
  ),
  custom: (
    <>
      <rect x="3" y="3" width="4.5" height="4.5" rx="0.6" />
      <rect x="8.5" y="8.5" width="4.5" height="4.5" rx="0.6" />
    </>
  ),
};

export function TreeIconSvg({
  kind,
  className,
  size = 16,
}: {
  kind: TreeIconKind;
  className?: string;
  size?: number;
}) {
  const classes = ["tree-icon-svg", `tree-icon--${kind}`, className].filter(Boolean).join(" ");
  return (
    <svg
      className={classes}
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.35"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      {ICONS[kind]}
    </svg>
  );
}

export default function ObjectTreeIcon({
  path,
  type,
  iconId,
  federated,
  className,
  size,
}: {
  path: string;
  type: ObjectType;
  iconId?: string | null;
  federated?: boolean;
  className?: string;
  size?: number;
}) {
  const kind = resolveObjectTreeIcon(path, type, iconId);
  return (
    <span className={`tree-icon-wrap${federated ? " tree-icon-wrap--federated" : ""}`}>
      <TreeIconSvg kind={kind} className={className} size={size} />
      {federated && <span className="tree-federated-badge" title="Federation bind">F</span>}
    </span>
  );
}
