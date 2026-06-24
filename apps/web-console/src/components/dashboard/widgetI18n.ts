import type { TFunction } from "i18next";
import type { WidgetType } from "../../types/dashboard";

export function widgetTypeI18nKey(type: WidgetType): string {
  return `type.${type.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())}`;
}

/** Widget type labels live in the `widgets` namespace, not `dashboard`. */
export function translateWidgetType(t: TFunction, type: WidgetType): string {
  return t(widgetTypeI18nKey(type), { ns: "widgets" });
}
