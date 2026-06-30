export type BundleSectionKey =
  | "objects"
  | "dashboards"
  | "workflows"
  | "models"
  | "migrations"
  | "functions"
  | "bindings"
  | "reports"
  | "alertRules"
  | "correlators"
  | "schedules"
  | "events";

export interface BundleSectionRow {
  section: BundleSectionKey;
  index: number;
  label: string;
}

const SECTION_KEYS: BundleSectionKey[] = [
  "objects",
  "dashboards",
  "workflows",
  "models",
  "migrations",
  "functions",
  "bindings",
  "reports",
  "alertRules",
  "correlators",
  "schedules",
  "events",
];

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function labelForItem(section: BundleSectionKey, item: Record<string, unknown>): string {
  switch (section) {
    case "objects":
      return `${String(item.parentPath ?? "?")}/${String(item.name ?? "?")}`;
    case "dashboards":
    case "workflows":
      return String(item.path ?? "?");
    case "models":
      return String(item.name ?? "?");
    case "migrations":
      return String(item.id ?? "?");
    case "functions":
      return `${String(item.objectPath ?? "?")}#${String(item.functionName ?? "?")}`;
    case "bindings":
      return `${String(item.objectPath ?? "?")}.${String(item.variable ?? "?")}`;
    case "reports":
      return String(item.reportId ?? "?");
    case "alertRules":
      return String(item.ruleId ?? item.path ?? "?");
    case "correlators":
      return String(item.correlatorId ?? item.path ?? "?");
    case "schedules":
      return String(item.scheduleId ?? item.path ?? "?");
    case "events":
      return String(item.id ?? "?");
    default:
      return "?";
  }
}

export function listBundleSectionRows(manifest: Record<string, unknown>): BundleSectionRow[] {
  const rows: BundleSectionRow[] = [];
  for (const section of SECTION_KEYS) {
    const items = manifest[section];
    if (!Array.isArray(items)) {
      continue;
    }
    items.forEach((raw, index) => {
      const item = asRecord(raw);
      if (!item) {
        return;
      }
      rows.push({
        section,
        index,
        label: labelForItem(section, item),
      });
    });
  }
  return rows;
}

export function removeBundleSectionItem(
  manifest: Record<string, unknown>,
  section: BundleSectionKey,
  index: number
): Record<string, unknown> {
  const next = structuredClone(manifest);
  const items = next[section];
  if (!Array.isArray(items) || index < 0 || index >= items.length) {
    return next;
  }
  next[section] = items.filter((_, itemIndex) => itemIndex !== index);
  return next;
}

export function appendBundleObject(
  manifest: Record<string, unknown>,
  entry: {
    parentPath: string;
    name: string;
    type: string;
    displayName?: string;
  }
): Record<string, unknown> {
  const next = structuredClone(manifest);
  const objects = Array.isArray(next.objects) ? [...next.objects] : [];
  objects.push({
    parentPath: entry.parentPath,
    name: entry.name,
    type: entry.type,
    displayName: entry.displayName ?? entry.name,
    description: "",
  });
  next.objects = objects;
  return next;
}

export function parseManifestJson(text: string): Record<string, unknown> {
  const parsed = JSON.parse(text) as unknown;
  const manifest = asRecord(parsed);
  if (!manifest) {
    throw new Error("Manifest must be a JSON object");
  }
  return manifest;
}
