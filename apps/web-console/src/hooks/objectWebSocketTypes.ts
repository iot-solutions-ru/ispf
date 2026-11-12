export interface ObjectWsMessage {
  type: "CREATED" | "UPDATED" | "DELETED" | "VARIABLE_UPDATED" | "EVENT_FIRED" | "presence";
  path: string;
  variableName: string;
  timestamp: string;
  revision?: number;
  changedBy?: string;
  /** Present on VARIABLE_UPDATED so UI can patch bindings without HTTP batch refetch. */
  value?: import("../types").DataRecord;
  /** Present when variable has includePreviousValueInEvent. */
  previousValue?: import("../types").DataRecord;
  /** Monotonic server cursor. The next subscribe sends this as {@code lastSeq}. */
  seq?: number;
}

export const OBJECT_WS_EVENT = "ispf-object-ws-message";
