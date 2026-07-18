export type CorrelatorActionType =
  | "RUN_WORKFLOW"
  | "FIRE_EVENT"
  | "SET_VARIABLE"
  | "OPEN_OPERATOR_REPORT"
  | "SEND_WEBHOOK"
  | "SEND_EMAIL";

export type CorrelatorPatternType = "COUNT" | "SEQUENCE" | "EVENT_CHAIN" | "WINDOW";

export interface CreateAlertRulePayload {
  name: string;
  objectPath: string;
  watchVariable: string;
  conditionExpr: string;
  eventName: string;
  payloadVariable?: string;
  enabled: boolean;
  edgeTrigger: boolean;
  delaySeconds?: number;
  sustainWhileTrue?: boolean;
  priority?: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
  ackRequired?: boolean;
  rateLimitSeconds?: number;
  deactivateExpr?: string;
  deactivateDelaySeconds?: number;
  pollIntervalMs?: number;
  triggerMessage?: string;
  clearEventName?: string;
  notificationWebhookUrl?: string;
  notificationEmailTarget?: string;
  anomalyModelId?: string;
}

/** Editable alert-rule fields shared by create dialog and inspector. */
export type AlertRuleFormValues = Omit<CreateAlertRulePayload, "name"> & {
  name?: string;
};

export interface AlertRuleRuntimeStatus {
  lastFiredAt?: string;
  lastConditionMet?: boolean;
  latchedActive?: boolean;
  conditionTrueSince?: string;
  deactivateTrueSince?: string;
};

export interface EventFilterPayload {
  filterId: string;
  displayName?: string;
  description?: string;
  eventNamePattern?: string;
  sourceObjectPathPattern?: string;
  minSeverity?: number;
  maxSeverity?: number;
  timeWindowMs?: number;
  filterExpression?: string;
  enabled?: boolean;
}

export interface CreateCorrelatorPayload {
  name: string;
  objectPath?: string;
  patternType?: CorrelatorPatternType;
  eventName: string;
  secondEventName?: string;
  windowSeconds: number;
  minOccurrences: number;
  cooldownSeconds: number;
  sequenceGapSeconds?: number;
  actionType: CorrelatorActionType;
  actionTarget: string;
  enabled: boolean;
}

export type WorkflowIspfAction =
  | "setVariable"
  | "invoke_function"
  | "fire_event"
  | "read_variable"
  | "start_workflow"
  | "log"
  | "publishNats"
  | "llm_complete"
  | "invoke_agent";

export const WORKFLOW_ISPF_ACTIONS: {
  action: WorkflowIspfAction;
  label: string;
  attrs: string[];
}[] = [
  { action: "fire_event", label: "Fire event", attrs: ["ispf:objectPath", "ispf:eventName", "ispf:payloadJson"] },
  { action: "read_variable", label: "Read variable", attrs: ["ispf:objectPath", "ispf:variable", "ispf:contextKey"] },
  { action: "start_workflow", label: "Start child workflow", attrs: ["ispf:workflowPath", "ispf:targetObject"] },
  { action: "invoke_function", label: "Invoke function", attrs: ["ispf:objectPath", "ispf:function", "ispf:inputJson"] },
  { action: "setVariable", label: "Set variable", attrs: ["ispf:targetObject", "ispf:variable", "ispf:value"] },
  { action: "log", label: "Log message", attrs: ["ispf:message"] },
  { action: "publishNats", label: "Publish NATS", attrs: ["ispf:subject", "ispf:message", "ispf:channel"] },
  {
    action: "llm_complete",
    label: "LLM complete",
    attrs: ["ispf:promptTemplate", "ispf:outputVariable", "ispf:outputFormat", "ispf:modelRef", "ispf:timeoutMs"],
  },
  {
    action: "invoke_agent",
    label: "Invoke agent",
    attrs: ["ispf:goalTemplate", "ispf:agentMode", "ispf:toolAllowlist", "ispf:maxSteps", "ispf:outputVariable"],
  },
];
