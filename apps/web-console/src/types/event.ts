import type { CorrelatorActionType, CorrelatorPatternType } from "./automation";

export type EventLevel = "INFO" | "WARNING" | "ERROR" | "CRITICAL";

export interface ObjectEvent {
  id: string;
  objectPath: string;
  eventName: string;
  level: EventLevel;
  payload: {
    schema: unknown;
    rows: Array<Record<string, unknown>>;
  };
  timestamp: string;
}

export interface AlertRule {
  id: string;
  name: string;
  objectPath: string;
  watchVariable: string;
  conditionExpr: string;
  eventName: string;
  payloadVariable: string | null;
  enabled: boolean;
  edgeTrigger: boolean;
  lastConditionMet: boolean | null;
  notificationWebhookUrl?: string | null;
  notificationEmailTarget?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EventCorrelator {
  id: string;
  name: string;
  objectPath: string | null;
  patternType: CorrelatorPatternType;
  eventName: string;
  secondEventName: string | null;
  windowSeconds: number;
  minOccurrences: number;
  cooldownSeconds: number;
  actionType: CorrelatorActionType;
  actionTarget: string;
  enabled: boolean;
  lastTriggeredAt: string | null;
  createdAt: string;
  updatedAt: string;
}
