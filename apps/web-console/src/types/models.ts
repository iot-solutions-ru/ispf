import type { EventDescriptor, FunctionDescriptor, ObjectType, DataRecord, DataSchema } from "../types";
import type { BindingActivators } from "../types";

export type ModelType = "RELATIVE" | "ABSOLUTE" | "INSTANCE";

export interface ModelVariableDefinition {
  name: string;
  description: string;
  group: string;
  schema: DataSchema;
  readable: boolean;
  writable: boolean;
  defaultValue: DataRecord | null;
  historyEnabled?: boolean;
  historyRetentionDays?: number | null;
}

export interface ModelBindingRule {
  id: string;
  name?: string | null;
  enabled?: boolean | null;
  order?: number | null;
  activators?: BindingActivators | null;
  condition?: string | null;
  expression: string;
  targetVariable: string;
  targetField?: string | null;
}

export interface ModelDto {
  id: string;
  name: string;
  description: string;
  type: ModelType;
  targetObjectType: ObjectType;
  suitabilityExpression: string;
  objectPath: string;
  variables: ModelVariableDefinition[];
  events: EventDescriptor[];
  functions: FunctionDescriptor[];
  bindings: ModelBindingRule[];
  parameters: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface ModelAttachmentDto {
  id: string;
  modelId: string;
  modelName: string;
  modelType: ModelType;
  objectPath: string;
  attachedAt: string;
}

export interface CreateModelPayload {
  name: string;
  description?: string;
  type?: ModelType;
  targetObjectType?: ObjectType;
  suitabilityExpression?: string;
  variables?: ModelVariableDefinition[];
  events?: EventDescriptor[];
  functions?: FunctionDescriptor[];
  bindings?: ModelBindingRule[];
  parameters?: Record<string, string>;
}

export interface UpdateModelPayload {
  name?: string;
  description?: string;
  type?: ModelType;
  targetObjectType?: ObjectType;
  suitabilityExpression?: string;
  variables?: ModelVariableDefinition[];
  events?: EventDescriptor[];
  functions?: FunctionDescriptor[];
  bindings?: ModelBindingRule[];
  parameters?: Record<string, string>;
}

export const MODELS_ROOT = "root.platform.models";

export const BUILTIN_MODEL_NAMES = new Set([
  "mqtt-sensor-v1",
  "dashboard-v1",
  "workflow-v1",
  "virtual-lab-v1",
  "snmp-agent-v1",
  "vendor-sensor-ext-v1",
]);

export function modelNameFromPath(path: string): string | null {
  if (!path.startsWith(MODELS_ROOT + ".")) {
    return null;
  }
  return path.slice(MODELS_ROOT.length + 1);
}

export function isModelsPath(path: string): boolean {
  return path === MODELS_ROOT || path.startsWith(MODELS_ROOT + ".");
}
