export interface ObjectWsMessage {
  type: "CREATED" | "UPDATED" | "DELETED" | "VARIABLE_UPDATED" | "EVENT_FIRED" | "presence";
  path: string;
  variableName: string;
  timestamp: string;
  revision?: number;
  changedBy?: string;
  /** Present when variable has includePreviousValueInEvent or API write with enrichment. */
  value?: import("../types").DataRecord;
  previousValue?: import("../types").DataRecord;
}

export const OBJECT_WS_EVENT = "ispf-object-ws-message";
