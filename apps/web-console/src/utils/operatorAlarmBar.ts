import type { ObjectEvent, EventLevel } from "../types/event";
import type {
  ActiveOperatorAlarm,
  AlarmDisplayField,
  OperatorAlarmBarConfig,
  OperatorAlarmRule,
} from "../types/operatorAlarmBar";
import type { OperatorUi } from "../types/operatorUi";
import { formatUserDateTime } from "./formatDateTime";

const PRIORITY_RANK: Record<string, number> = {
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
  CRITICAL: 4,
};

const LEVEL_RANK: Record<EventLevel, number> = {
  INFO: 1,
  WARNING: 2,
  ERROR: 3,
  CRITICAL: 4,
};

export function resolveEventPriority(
  event: ObjectEvent,
  alertRules?: import("../types/event").AlertRule[]
): number {
  const fromPayload = payloadValue(event, "alertPriority");
  if (typeof fromPayload === "string" && PRIORITY_RANK[fromPayload]) {
    return PRIORITY_RANK[fromPayload];
  }
  const match = alertRules?.find(
    (rule) => rule.objectPath === event.objectPath && rule.eventName === event.eventName
  );
  if (match?.priority && PRIORITY_RANK[match.priority]) {
    return PRIORITY_RANK[match.priority];
  }
  return LEVEL_RANK[event.level];
}

export function isAlarmShelved(
  event: ObjectEvent,
  shelves: import("../api").AlarmShelf[]
): boolean {
  return shelves.some(
    (shelf) =>
      shelf.active &&
      shelf.objectPath === event.objectPath &&
      shelf.eventName === event.eventName &&
      (!shelf.expiresAt || new Date(shelf.expiresAt).getTime() > Date.now())
  );
}

const DEFAULT_SOUND_URL = "/sounds/alarm.wav";
const DEFAULT_REPEAT_MS = 3000;

const DEFAULT_FIELDS: AlarmDisplayField[] = [
  { label: "Событие", source: "eventName" },
  { label: "Объект", source: "objectPath" },
  { label: "Время", source: "timestamp" },
];

const LEVEL_COLORS: Record<EventLevel, NonNullable<OperatorAlarmRule["colors"]>> = {
  INFO: { background: "#1e3a5f", text: "#e0f2fe", border: "#38bdf8", accent: "#7dd3fc" },
  WARNING: { background: "#422006", text: "#fef3c7", border: "#f59e0b", accent: "#fbbf24" },
  ERROR: { background: "#450a0a", text: "#fee2e2", border: "#ef4444", accent: "#f87171" },
  CRITICAL: { background: "#3b0000", text: "#fecaca", border: "#dc2626", accent: "#ef4444" },
};

export function isAlarmBarEnabled(config?: OperatorAlarmBarConfig | null): boolean {
  return config?.enabled !== false;
}

export function resolveAlarmBarConfig(config?: OperatorAlarmBarConfig | null): Required<
  Pick<OperatorAlarmBarConfig, "soundEnabled" | "soundUrl" | "soundRepeatMs" | "minLevel" | "position">
> & { rules: OperatorAlarmRule[] } {
  return {
    soundEnabled: config?.soundEnabled !== false,
    soundUrl: config?.soundUrl ?? DEFAULT_SOUND_URL,
    soundRepeatMs: config?.soundRepeatMs ?? DEFAULT_REPEAT_MS,
    minLevel: config?.minLevel ?? "ERROR",
    position: config?.position ?? "top",
    rules: config?.rules?.length ? config.rules : [defaultAlarmRule()],
  };
}

function defaultAlarmRule(): OperatorAlarmRule {
  return {
    id: "default",
    minLevel: "ERROR",
    fields: DEFAULT_FIELDS,
    persistUntilDismiss: true,
  };
}

export function eventMeetsLevel(eventLevel: EventLevel, minLevel: EventLevel): boolean {
  return LEVEL_RANK[eventLevel] >= LEVEL_RANK[minLevel];
}

export function matchAlarmRule(
  event: ObjectEvent,
  rules: OperatorAlarmRule[],
  globalMinLevel: EventLevel
): OperatorAlarmRule | null {
  for (const rule of rules) {
    const ruleMin = rule.minLevel ?? globalMinLevel;
    if (!eventMeetsLevel(event.level, ruleMin)) {
      continue;
    }
    if (rule.eventNames?.length && !rule.eventNames.includes(event.eventName)) {
      continue;
    }
    if (rule.objectPathPrefix && !event.objectPath.startsWith(rule.objectPathPrefix)) {
      continue;
    }
    return rule;
  }
  return null;
}

/** Platform alert-rule node that fired this event (events usually land on the rule path). */
export function findPlatformAlertRuleForEvent(
  event: ObjectEvent,
  alertRules: import("../types/event").AlertRule[],
): import("../types/event").AlertRule | undefined {
  const byId = alertRules.find((rule) => rule.id === event.objectPath);
  if (byId) {
    return byId;
  }
  return alertRules.find((rule) => rule.eventName === event.eventName && rule.enabled);
}

/** True when the platform alert rule still considers the condition active. */
export function isPlatformAlertConditionActive(
  rule: import("../types/event").AlertRule | undefined,
): boolean {
  if (!rule) {
    return false;
  }
  return rule.lastConditionMet === true || rule.latchedActive === true;
}

/**
 * Historical / polled events should only resurface the bar while the condition is still true.
 * Live EVENT_FIRED bypasses this (condition just became true).
 */
export function shouldResurfaceAlarmFromFeed(
  event: ObjectEvent,
  operatorRule: OperatorAlarmRule,
  alertRules: import("../types/event").AlertRule[],
): boolean {
  if (operatorRule.persistUntilDismiss) {
    return true;
  }
  return isPlatformAlertConditionActive(findPlatformAlertRuleForEvent(event, alertRules));
}

export function payloadValue(event: ObjectEvent, key: string): unknown {
  const row = event.payload?.rows?.[0];
  if (!row) {
    return undefined;
  }
  return row[key];
}

export function resolveAlarmNavigateParams(
  event: ObjectEvent,
  rule: OperatorAlarmRule
): Record<string, unknown> {
  const params: Record<string, unknown> = { ...(rule.actions?.sessionParams ?? {}) };
  const fromPayload = rule.actions?.sessionParamsFromPayload;
  if (!fromPayload) {
    return params;
  }
  for (const [paramKey, payloadKey] of Object.entries(fromPayload)) {
    const raw = payloadValue(event, payloadKey);
    if (raw !== undefined && raw !== null && String(raw).trim() !== "") {
      params[paramKey] = String(raw);
    }
  }
  return params;
}

export function resolveFieldValue(event: ObjectEvent, source: string): string {
  if (source === "eventName") {
    return event.eventName;
  }
  if (source === "objectPath") {
    return event.objectPath;
  }
  if (source === "level") {
    return event.level;
  }
  if (source === "timestamp") {
    return formatUserDateTime(event.timestamp);
  }
  if (source.startsWith("payload.")) {
    const key = source.slice("payload.".length);
    const raw = payloadValue(event, key);
    if (raw === undefined || raw === null) {
      return "—";
    }
    const unit = payloadValue(event, "unit");
    if (key === "value" && unit) {
      return `${String(raw)} ${String(unit)}`;
    }
    return String(raw);
  }
  if (source === "label") {
    return "";
  }
  return source;
}

export function renderAlarmTitle(template: string | undefined, event: ObjectEvent): string {
  const base = template?.trim() || "{{eventName}}";
  return base
    .replace(/\{\{eventName\}\}/g, event.eventName)
    .replace(/\{\{objectPath\}\}/g, event.objectPath)
    .replace(/\{\{level\}\}/g, event.level);
}

function resolveColors(
  rule: OperatorAlarmRule,
  event: ObjectEvent
): ActiveOperatorAlarm["colors"] {
  const defaults = LEVEL_COLORS[event.level] ?? LEVEL_COLORS.ERROR;
  return {
    background: rule.colors?.background ?? defaults.background ?? "#450a0a",
    text: rule.colors?.text ?? defaults.text ?? "#fee2e2",
    border: rule.colors?.border ?? defaults.border ?? "#ef4444",
    accent: rule.colors?.accent ?? defaults.accent ?? "#f87171",
  };
}

function resolveDashboardPath(
  event: ObjectEvent,
  rule: OperatorAlarmRule,
  ui?: OperatorUi
): string | null {
  const actions = rule.actions;
  if (actions?.dashboardPath) {
    return actions.dashboardPath;
  }
  if (actions?.dashboardFromPayload) {
    const raw = payloadValue(event, actions.dashboardFromPayload);
    if (typeof raw === "string" && raw.trim()) {
      return raw.trim();
    }
  }
  if (event.eventName === "openOperatorReport") {
    return null;
  }
  if (ui?.dashboards.some((item) => item.path === event.objectPath)) {
    return event.objectPath;
  }
  const prefixMatch = ui?.dashboards.find((item) =>
    event.objectPath.startsWith(item.path.replace(/\.dashboards\..+$/, ""))
  );
  return prefixMatch?.path ?? null;
}

function resolveReportPath(event: ObjectEvent, rule: OperatorAlarmRule): string | null {
  const key = rule.actions?.reportFromPayload ?? "reportPath";
  if (event.eventName === "openOperatorReport" || rule.actions?.reportFromPayload) {
    const raw = payloadValue(event, key);
    if (typeof raw === "string" && raw.trim()) {
      return raw.trim();
    }
  }
  return null;
}

export function buildActiveAlarm(
  event: ObjectEvent,
  rule: OperatorAlarmRule,
  config: ReturnType<typeof resolveAlarmBarConfig>,
  ui?: OperatorUi,
  alertRules?: import("../types/event").AlertRule[]
): ActiveOperatorAlarm {
  const fields = rule.fields?.length ? rule.fields : DEFAULT_FIELDS;
  const fieldRows = fields
    .filter((field) => field.source !== "label")
    .map((field) => ({
      label: field.label,
      value: field.source === "label" ? field.label : resolveFieldValue(event, field.source),
    }));

  const ruleSoundEnabled = rule.sound?.enabled !== false;
  const soundEnabled = config.soundEnabled && ruleSoundEnabled;
  const soundUrl = rule.sound?.url ?? config.soundUrl;

  const matchingAlertRule = alertRules?.find(
    (item) => item.objectPath === event.objectPath && item.eventName === event.eventName
  );
  const ackRequired = matchingAlertRule?.ackRequired === true;

  return {
    id: event.id,
    event,
    rule,
    title: renderAlarmTitle(rule.title, event),
    fieldRows,
    colors: resolveColors(rule, event),
    soundUrl: soundEnabled ? soundUrl : null,
    soundEnabled,
    dashboardPath: resolveDashboardPath(event, rule, ui),
    reportPath: resolveReportPath(event, rule),
    selectionKey: rule.actions?.selectionKey ?? "objectPath",
    acknowledgeFunction: rule.actions?.acknowledgeFunction ?? null,
    primaryActionLabel: rule.actions?.primaryActionLabel?.trim() || null,
    hideSecondaryActions: rule.actions?.hideSecondaryActions === true,
    hideAcknowledge: ackRequired ? false : rule.actions?.hideAcknowledge === true,
    ackRequired,
    navigateParams: resolveAlarmNavigateParams(event, rule),
  };
}

export function compareAlarmPriority(
  a: ActiveOperatorAlarm,
  b: ActiveOperatorAlarm,
  alertRules?: import("../types/event").AlertRule[]
): number {
  const priorityDiff =
    resolveEventPriority(b.event, alertRules) - resolveEventPriority(a.event, alertRules);
  if (priorityDiff !== 0) {
    return priorityDiff;
  }
  const levelDiff = LEVEL_RANK[b.event.level] - LEVEL_RANK[a.event.level];
  if (levelDiff !== 0) {
    return levelDiff;
  }
  return new Date(b.event.timestamp).getTime() - new Date(a.event.timestamp).getTime();
}

export const ALARM_MUTE_STORAGE_KEY = "ispf-operator-alarm-muted";
