import type { ObjectEvent, EventLevel } from "../types/event";
import type {
  ActiveOperatorAlarm,
  AlarmDisplayField,
  OperatorAlarmBarConfig,
  OperatorAlarmRule,
} from "../types/operatorAlarmBar";
import type { OperatorUi } from "../types/operatorUi";

const LEVEL_RANK: Record<EventLevel, number> = {
  INFO: 1,
  WARNING: 2,
  ERROR: 3,
  CRITICAL: 4,
};

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

function payloadValue(event: ObjectEvent, key: string): unknown {
  const row = event.payload?.rows?.[0];
  if (!row) {
    return undefined;
  }
  return row[key];
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
    return new Date(event.timestamp).toLocaleString();
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
  ui?: OperatorUi
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
  };
}

export function compareAlarmPriority(a: ActiveOperatorAlarm, b: ActiveOperatorAlarm): number {
  const levelDiff = LEVEL_RANK[b.event.level] - LEVEL_RANK[a.event.level];
  if (levelDiff !== 0) {
    return levelDiff;
  }
  return new Date(b.event.timestamp).getTime() - new Date(a.event.timestamp).getTime();
}

export const ALARM_MUTE_STORAGE_KEY = "ispf-operator-alarm-muted";
