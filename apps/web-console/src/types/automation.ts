export type CorrelatorActionType = "RUN_WORKFLOW";

export interface CreateAlertRulePayload {
  name: string;
  objectPath: string;
  watchVariable: string;
  conditionExpr: string;
  eventName: string;
  payloadVariable?: string;
  enabled: boolean;
  edgeTrigger: boolean;
}

export interface CreateCorrelatorPayload {
  name: string;
  objectPath?: string;
  eventName: string;
  windowSeconds: number;
  minOccurrences: number;
  cooldownSeconds: number;
  actionType: CorrelatorActionType;
  actionTarget: string;
  enabled: boolean;
}
