import type { EventDescriptor, FunctionDescriptor, ObjectType, DataRecord, DataSchema } from "../types";

export type ModelType = "RELATIVE" | "ABSOLUTE" | "INSTANCE";

export interface ModelVariableDefinition {
  name: string;
  description: string;
  group: string;
  schema: DataSchema;
  readable: boolean;
  writable: boolean;
  defaultBinding: string | null;
  defaultValue: DataRecord | null;
  historyEnabled?: boolean;
  historyRetentionDays?: number | null;
}

export interface ModelBindingDefinition {
  targetVariable: string;
  expression: string;
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
  bindings: ModelBindingDefinition[];
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
  bindings?: ModelBindingDefinition[];
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
  bindings?: ModelBindingDefinition[];
  parameters?: Record<string, string>;
}

export const MODELS_ROOT = "root.platform.models";

export const BUILTIN_MODEL_NAMES = new Set([
  "mqtt-sensor-v1",
  "dashboard-v1",
  "workflow-v1",
  "snmp-agent-v1",
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
