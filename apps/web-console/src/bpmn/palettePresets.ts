import type { CatchKind, FlowNodeType, IspfProps } from "./model/types";

export interface PalettePreset {
  id: string;
  labelKey: string;
  group: "event" | "activity" | "gateway" | "ai" | "marketplace";
  type: FlowNodeType;
  catchKind?: CatchKind;
  name: string;
  ispf?: IspfProps;
}

export const PLATFORM_PALETTE: PalettePreset[] = [
  { id: "start", labelKey: "bpmn.palette.start", group: "event", type: "startEvent", name: "Start" },
  { id: "end", labelKey: "bpmn.palette.end", group: "event", type: "endEvent", name: "End" },
  {
    id: "catch-timer",
    labelKey: "bpmn.palette.catchTimer",
    group: "event",
    type: "intermediateCatchEvent",
    catchKind: "timer",
    name: "Timer",
    ispf: { durationSeconds: "60" },
  },
  {
    id: "catch-signal",
    labelKey: "bpmn.palette.catchSignal",
    group: "event",
    type: "intermediateCatchEvent",
    catchKind: "signal",
    name: "Signal",
    ispf: { signal: "signalName" },
  },
  {
    id: "catch-message",
    labelKey: "bpmn.palette.catchMessage",
    group: "event",
    type: "intermediateCatchEvent",
    catchKind: "message",
    name: "Message",
    ispf: { message: "messageName" },
  },
  {
    id: "throw-message",
    labelKey: "bpmn.palette.throwMessage",
    group: "event",
    type: "intermediateThrowEvent",
    name: "Throw message",
    ispf: { message: "messageName" },
  },
  {
    id: "service-log",
    labelKey: "bpmn.palette.serviceLog",
    group: "activity",
    type: "serviceTask",
    name: "Log",
    ispf: { action: "log", message: "hello" },
  },
  {
    id: "service-set",
    labelKey: "bpmn.palette.serviceSetVariable",
    group: "activity",
    type: "serviceTask",
    name: "Set variable",
    ispf: { action: "setVariable", variable: "status", value: "ok" },
  },
  {
    id: "service-fn",
    labelKey: "bpmn.palette.serviceInvokeFunction",
    group: "activity",
    type: "serviceTask",
    name: "Invoke function",
    ispf: { action: "invoke_function", objectPath: "", functionName: "" },
  },
  {
    id: "user",
    labelKey: "bpmn.palette.userTask",
    group: "activity",
    type: "userTask",
    name: "User task",
    ispf: { title: "Confirm", assigneeRole: "operator", instructions: "" },
  },
  {
    id: "message-task",
    labelKey: "bpmn.palette.messageTask",
    group: "activity",
    type: "messageTask",
    name: "Publish NATS",
    ispf: { subject: "ispf.workflow.message", message: "hello", channel: "nats" },
  },
  {
    id: "call-activity",
    labelKey: "bpmn.palette.callActivity",
    group: "activity",
    type: "callActivity",
    name: "Call workflow",
    ispf: { workflowPath: "root.platform.workflows.child" },
  },
  {
    id: "xor",
    labelKey: "bpmn.palette.exclusiveGateway",
    group: "gateway",
    type: "exclusiveGateway",
    name: "Gateway",
  },
  {
    id: "and",
    labelKey: "bpmn.palette.parallelGateway",
    group: "gateway",
    type: "parallelGateway",
    name: "Parallel",
  },
  {
    id: "llm",
    labelKey: "bpmn.palette.llmComplete",
    group: "ai",
    type: "serviceTask",
    name: "LLM Complete",
    ispf: {
      action: "llm_complete",
      promptTemplate: "Classify: ${alarmMessage}. Reply JSON {severity,reason}",
      outputVariable: "llmClassification",
      outputFormat: "json",
      modelRef: "platform-default",
    },
  },
  {
    id: "agent",
    labelKey: "bpmn.palette.invokeAgent",
    group: "ai",
    type: "serviceTask",
    name: "Invoke Agent",
    ispf: {
      action: "invoke_agent",
      goalTemplate: "Explain trend for ${tagPath} last 4h",
      agentMode: "ask",
      toolAllowlist: "get_variable_history,summarize_trend,detect_anomalies",
      maxSteps: "8",
      outputVariable: "agentBrief",
    },
  },
];
