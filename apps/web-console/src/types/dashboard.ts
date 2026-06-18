export type WidgetType = "value" | "toggle" | "indicator" | "chart" | "sparkline" | "function";

export type ChartStyle = "line" | "area";

export interface DashboardWidgetBase {
  id: string;
  type: WidgetType;
  title: string;
  x: number;
  y: number;
  w: number;
  h: number;
  objectPath: string;
  variableName: string;
  valueField?: string;
}

export interface ValueWidget extends DashboardWidgetBase {
  type: "value";
  unit?: string;
  unitField?: string;
  decimals?: number;
}

export interface ToggleWidget extends DashboardWidgetBase {
  type: "toggle";
  trueLabel?: string;
  falseLabel?: string;
}

export interface IndicatorWidget extends DashboardWidgetBase {
  type: "indicator";
  trueLabel?: string;
  falseLabel?: string;
  trueColor?: string;
  falseColor?: string;
}

export interface ChartWidget extends DashboardWidgetBase {
  type: "chart";
  chartStyle?: ChartStyle;
  maxPoints?: number;
  color?: string;
  decimals?: number;
  unit?: string;
  unitField?: string;
}

export interface SparklineWidget extends DashboardWidgetBase {
  type: "sparkline";
  maxPoints?: number;
  color?: string;
  decimals?: number;
}

export interface FunctionWidget extends DashboardWidgetBase {
  type: "function";
  functionName: string;
  buttonLabel?: string;
  confirmMessage?: string;
}

export type DashboardWidget =
  | ValueWidget
  | ToggleWidget
  | IndicatorWidget
  | ChartWidget
  | SparklineWidget
  | FunctionWidget;

export interface DashboardLayout {
  columns: number;
  rowHeight: number;
  widgets: DashboardWidget[];
}

export interface DashboardView {
  path: string;
  title: string;
  refreshIntervalMs: number;
  layout: DashboardLayout;
  layoutJson: string;
}

export const WIDGET_TYPES: Array<{ type: WidgetType; label: string }> = [
  { type: "value", label: "Значение" },
  { type: "indicator", label: "Индикатор" },
  { type: "toggle", label: "Переключатель" },
  { type: "chart", label: "График / тренд" },
  { type: "sparkline", label: "Спарклайн" },
  { type: "function", label: "Функция (кнопка)" },
];

export function emptyLayout(): DashboardLayout {
  return { columns: 12, rowHeight: 72, widgets: [] };
}

export function parseLayoutJson(raw: string | undefined | null): DashboardLayout {
  if (!raw) {
    return emptyLayout();
  }
  try {
    const parsed = JSON.parse(raw) as DashboardLayout;
    return {
      columns: parsed.columns ?? 12,
      rowHeight: parsed.rowHeight ?? 72,
      widgets: Array.isArray(parsed.widgets) ? parsed.widgets : [],
    };
  } catch {
    return emptyLayout();
  }
}

export function layoutToJson(layout: DashboardLayout): string {
  return JSON.stringify(layout, null, 2);
}

export function newWidget(type: WidgetType, index: number): DashboardWidget {
  const base = {
    id: `widget-${Date.now()}-${index}`,
    title: "Виджет",
    x: 0,
    y: index * 2,
    w: 3,
    h: 2,
    objectPath: "",
    variableName: "",
    valueField: "value",
  };
  switch (type) {
    case "toggle":
      return { ...base, type: "toggle", trueLabel: "Вкл", falseLabel: "Выкл" };
    case "indicator":
      return { ...base, type: "indicator", trueLabel: "Да", falseLabel: "Нет" };
    case "chart":
      return {
        ...base,
        type: "chart",
        w: 6,
        h: 4,
        chartStyle: "area",
        maxPoints: 120,
        color: "#2f81f7",
        decimals: 1,
      };
    case "sparkline":
      return {
        ...base,
        type: "sparkline",
        w: 4,
        h: 2,
        maxPoints: 40,
        color: "#3fb950",
        decimals: 1,
      };
    case "function":
      return {
        ...base,
        type: "function",
        w: 3,
        h: 2,
        functionName: "acknowledgeAlarm",
        buttonLabel: "Выполнить",
        objectPath: "",
      };
    default:
      return { ...base, type: "value", decimals: 1 };
  }
}

export function readFieldValue(
  row: Record<string, unknown> | undefined,
  field?: string
): unknown {
  if (!row) return undefined;
  if (!field) return row.value ?? row.raw ?? Object.values(row)[0];
  return row[field];
}

export function mergeWidgetLayout(
  widgets: DashboardWidget[],
  positions: Array<{ id: string; x: number; y: number; w: number; h: number }>
): DashboardWidget[] {
  const byId = new Map(positions.map((item) => [item.id, item]));
  return widgets.map((widget) => {
    const position = byId.get(widget.id);
    if (!position) return widget;
    return { ...widget, x: position.x, y: position.y, w: position.w, h: position.h };
  });
}
