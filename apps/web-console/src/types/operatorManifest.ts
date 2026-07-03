/** Declarative operator app — mapping screens to BFF invoke (app bundle / public manifest). */

import type { ChartType, ChartWidget, MapWidget, WidgetHistoryRange } from "./dashboard";

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

/** Embedded platform dashboard (object path to DASHBOARD). */
export interface OperatorManifestDashboard {
  dashboardPath: string;
  refreshIntervalMs?: number;
  /** Optional JSON object for dashboard session selection keys. */
  selectionJson?: string;
}

/** Single-variable chart screen (reuses chart widget bindings). */
export interface OperatorManifestChart {
  objectPath: string;
  variableName?: string;
  valueField?: string;
  chartType?: ChartType;
  chartStyle?: "line" | "area";
  historyRange?: WidgetHistoryRange;
  maxPoints?: number;
  refreshIntervalMs?: number;
  color?: string;
  decimals?: number;
  unit?: string;
  unitField?: string;
  bubbleXVariable?: string;
  bubbleYVariable?: string;
  bubbleSizeVariable?: string;
  bubbleDefaultSize?: number;
  bubblePointsJson?: string;
  radarAxesJson?: string;
  demoPreviewJson?: string;
}

/** Map of child devices under parentPath (same fields as map widget). */
export interface OperatorManifestMap {
  parentPath: string;
  latVariable?: string;
  latField?: string;
  lonField?: string;
  labelVariable?: string;
  zoom?: number;
  centerLat?: number;
  centerLon?: number;
  refreshIntervalMs?: number;
  tileUrl?: string;
  tileAttribution?: string;
  rowTargetDashboard?: string;
  rowOpenMode?: "navigate" | "modal";
}

export type OperatorManifestScreenKind =
  | "dashboard"
  | "chart"
  | "map"
  | "report"
  | "table"
  | "empty";

export interface OperatorManifestScreen {
  id: string;
  title: string;
  description?: string;
  /** When false, screen data is not cached for offline operator mode. Default: true for data screens. */
  offlineCache?: boolean;
  actions?: OperatorManifestAction[];
  table?: OperatorManifestTable;
  report?: OperatorManifestReport;
  dashboard?: OperatorManifestDashboard;
  chart?: OperatorManifestChart;
  map?: OperatorManifestMap;
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

export function resolveManifestScreenKind(screen: OperatorManifestScreen): OperatorManifestScreenKind {
  if (screen.dashboard?.dashboardPath) {
    return "dashboard";
  }
  if (screen.chart?.objectPath && screen.chart.variableName) {
    return "chart";
  }
  if (screen.chart?.bubbleXVariable && screen.chart.bubbleYVariable) {
    return "chart";
  }
  if (screen.chart?.radarAxesJson) {
    return "chart";
  }
  if (screen.map?.parentPath) {
    return "map";
  }
  if (screen.report?.reportId) {
    return "report";
  }
  if (screen.table?.objectPath && screen.table.functionName) {
    return "table";
  }
  return "empty";
}

export function manifestChartToWidget(
  screen: OperatorManifestScreen,
  chart: OperatorManifestChart
): ChartWidget {
  return {
    id: `manifest-chart-${screen.id}`,
    type: "chart",
    title: screen.title,
    x: 0,
    y: 0,
    w: 12,
    h: 4,
    objectPath: chart.objectPath,
    variableName: chart.variableName ?? "",
    valueField: chart.valueField ?? "value",
    chartType: chart.chartType ?? chart.chartStyle ?? "area",
    chartStyle: chart.chartStyle ?? "area",
    historyRange: chart.historyRange ?? "live",
    maxPoints: chart.maxPoints ?? 120,
    color: chart.color,
    decimals: chart.decimals,
    unit: chart.unit,
    unitField: chart.unitField,
    bubbleXVariable: chart.bubbleXVariable,
    bubbleYVariable: chart.bubbleYVariable,
    bubbleSizeVariable: chart.bubbleSizeVariable,
    bubbleDefaultSize: chart.bubbleDefaultSize,
    bubblePointsJson: chart.bubblePointsJson,
    radarAxesJson: chart.radarAxesJson,
    demoPreviewJson: chart.demoPreviewJson,
  };
}

export function manifestMapToWidget(
  screen: OperatorManifestScreen,
  map: OperatorManifestMap
): MapWidget {
  return {
    id: `manifest-map-${screen.id}`,
    type: "map",
    title: screen.title,
    x: 0,
    y: 0,
    w: 12,
    h: 5,
    parentPath: map.parentPath,
    latVariable: map.latVariable,
    latField: map.latField,
    lonField: map.lonField,
    labelVariable: map.labelVariable,
    zoom: map.zoom,
    centerLat: map.centerLat,
    centerLon: map.centerLon,
    tileUrl: map.tileUrl,
    tileAttribution: map.tileAttribution,
    rowTargetDashboard: map.rowTargetDashboard,
    rowOpenMode: map.rowOpenMode,
  };
}

export function parseManifestSelectionJson(raw: string | undefined): Record<string, string> {
  if (!raw?.trim()) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return {};
    }
    const result: Record<string, string> = {};
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (value != null && String(value).trim() !== "") {
        result[key] = String(value);
      }
    }
    return result;
  } catch {
    return {};
  }
}
