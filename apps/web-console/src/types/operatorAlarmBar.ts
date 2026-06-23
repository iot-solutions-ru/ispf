import type { EventLevel } from "./event";

export interface AlarmDisplayField {
  label: string;
  source: string;
}

export interface OperatorAlarmRule {
  id: string;
  eventNames?: string[];
  objectPathPrefix?: string;
  minLevel?: EventLevel;
  title?: string;
  fields?: AlarmDisplayField[];
  colors?: {
    background?: string;
    text?: string;
    border?: string;
    accent?: string;
  };
  sound?: { enabled?: boolean; url?: string };
  actions?: {
    dashboardPath?: string;
    dashboardFromPayload?: string;
    selectionKey?: string;
    reportFromPayload?: string;
    acknowledgeFunction?: string;
  };
  persistUntilDismiss?: boolean;
}

export interface OperatorAlarmBarConfig {
  enabled?: boolean;
  soundEnabled?: boolean;
  soundUrl?: string;
  soundRepeatMs?: number;
  minLevel?: EventLevel;
  position?: "top" | "bottom";
  rules?: OperatorAlarmRule[];
}

export interface ActiveOperatorAlarm {
  id: string;
  event: import("./event").ObjectEvent;
  rule: OperatorAlarmRule;
  title: string;
  fieldRows: Array<{ label: string; value: string }>;
  colors: Required<OperatorAlarmRule["colors"]> & Record<string, string>;
  soundUrl: string | null;
  soundEnabled: boolean;
  dashboardPath: string | null;
  reportPath: string | null;
  selectionKey: string | null;
  acknowledgeFunction: string | null;
}
