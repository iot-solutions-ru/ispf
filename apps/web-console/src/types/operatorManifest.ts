/** Declarative operator app — mapping screens to BFF invoke (app bundle / public manifest). */

export type OperatorFieldType = "STRING" | "DOUBLE" | "BOOLEAN" | "LONG" | "INTEGER" | "SELECT" | "TEXT";

export interface OperatorManifestSelectOption {
  value: string;
  label: string;
}

export interface OperatorManifestOptionsFrom {
  objectPath: string;
  functionName: string;
  input?: Record<string, unknown>;
  valueField: string;
  labelField: string;
  /** When set, only rows where row[filterField] equals filterValue are shown. */
  filterField?: string;
  filterValue?: string | number | boolean;
}

export interface OperatorManifestField {
  name: string;
  label?: string;
  type?: OperatorFieldType;
  required?: boolean;
  hidden?: boolean;
  placeholder?: string;
  default?: unknown;
  options?: OperatorManifestSelectOption[];
  optionsFrom?: OperatorManifestOptionsFrom;
  /** Copy value from selected table row (requires table.selectable). */
  bindFromSelection?: string;
  /** Display-only; value still submitted from bindFromSelection or default. */
  readOnly?: boolean;
}

export interface OperatorManifestShowWhen {
  field: string;
  equals?: string | number | boolean;
  /** Show when field is null/empty (mutually exclusive with equals). */
  empty?: boolean;
  /** Show when field has a value (mutually exclusive with equals). */
  present?: boolean;
}

export interface OperatorManifestAction {
  id: string;
  label: string;
  objectPath: string;
  functionName: string;
  /** Static values merged into invoke input (overridden by form fields). */
  input?: Record<string, unknown>;
  successMessage?: string;
  requiresSelection?: boolean;
  showWhen?: OperatorManifestShowWhen;
  /** All conditions must match (AND). Takes precedence over showWhen when set. */
  showWhenAll?: OperatorManifestShowWhen[];
  fields?: OperatorManifestField[];
}

export interface OperatorManifestTable {
  objectPath: string;
  functionName: string;
  input?: Record<string, unknown>;
  refreshIntervalMs?: number;
  emptyMessage?: string;
  selectable?: boolean;
  /** Row key for selection; default order_id. */
  selectionKey?: string;
}

export interface OperatorManifestReport {
  reportId: string;
  parameters?: Record<string, unknown>;
  refreshIntervalMs?: number;
  emptyMessage?: string;
}

export interface OperatorManifestScreen {
  id: string;
  title: string;
  description?: string;
  actions?: OperatorManifestAction[];
  table?: OperatorManifestTable;
  report?: OperatorManifestReport;
}

export interface OperatorManifest {
  appId: string;
  title: string;
  wireProfile?: string;
  defaultScreen: string;
  screens: OperatorManifestScreen[];
}

export function resolveOperatorScreen(manifest: OperatorManifest, screenId: string | null): OperatorManifestScreen {
  const found = manifest.screens.find((screen) => screen.id === screenId);
  if (found) {
    return found;
  }
  const fallback = manifest.screens.find((screen) => screen.id === manifest.defaultScreen);
  if (fallback) {
    return fallback;
  }
  return manifest.screens[0];
}

export function selectionKeyForTable(table: OperatorManifestTable | undefined): string {
  return table?.selectionKey ?? "order_id";
}
