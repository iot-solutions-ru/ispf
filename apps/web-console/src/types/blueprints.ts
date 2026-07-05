import type { EventDescriptor, FunctionDescriptor, ObjectType, DataRecord, DataSchema } from "../types";
import type { BindingActivators } from "../types";

export type BlueprintType = "RELATIVE" | "ABSOLUTE" | "INSTANCE";

export interface BlueprintVariableDefinition {
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

export interface BlueprintBindingRule {
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

export interface BlueprintDto {
  id: string;
  name: string;
  description: string;
  type: BlueprintType;
  targetObjectType: ObjectType;
  suitabilityExpression: string;
  objectPath: string;
  variables: BlueprintVariableDefinition[];
  events: EventDescriptor[];
  functions: FunctionDescriptor[];
  bindings: BlueprintBindingRule[];
  parameters: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface BlueprintMergeWarningDto {
  kind: string;
  name: string;
  previousBlueprintId: string | null;
  appliedBlueprintId: string;
}

export interface BlueprintAttachmentDto {
  id: string;
  blueprintId: string;
  blueprintName: string;
  blueprintType: BlueprintType;
  objectPath: string;
  attachedAt: string;
  warnings?: BlueprintMergeWarningDto[];
}

export interface CreateBlueprintPayload {
  name: string;
  description?: string;
  type?: BlueprintType;
  targetObjectType?: ObjectType;
  suitabilityExpression?: string;
  variables?: BlueprintVariableDefinition[];
  events?: EventDescriptor[];
  functions?: FunctionDescriptor[];
  bindings?: BlueprintBindingRule[];
  parameters?: Record<string, string>;
}

export interface UpdateBlueprintPayload {
  name?: string;
  description?: string;
  type?: BlueprintType;
  targetObjectType?: ObjectType;
  suitabilityExpression?: string;
  variables?: BlueprintVariableDefinition[];
  events?: EventDescriptor[];
  functions?: FunctionDescriptor[];
  bindings?: BlueprintBindingRule[];
  parameters?: Record<string, string>;
}

export const RELATIVE_BLUEPRINTS_ROOT = "root.platform.relative-blueprints";
export const INSTANCE_TYPES_ROOT = "root.platform.instance-types";
export const ABSOLUTE_BLUEPRINTS_ROOT = "root.platform.absolute-blueprints";

export const BLUEPRINT_CATALOG_ROOTS = [
  RELATIVE_BLUEPRINTS_ROOT,
  INSTANCE_TYPES_ROOT,
  ABSOLUTE_BLUEPRINTS_ROOT,
] as const;

export type BlueprintCatalogRoot = (typeof BLUEPRINT_CATALOG_ROOTS)[number];

export const BUILTIN_BLUEPRINT_NAMES = new Set([
  "dashboard-v1",
  "workflow-v1",
  "virtual-lab-v1",
]);

export function catalogRootForBlueprintType(type: BlueprintType): BlueprintCatalogRoot {
  switch (type) {
    case "RELATIVE":
      return RELATIVE_BLUEPRINTS_ROOT;
    case "ABSOLUTE":
      return ABSOLUTE_BLUEPRINTS_ROOT;
    default:
      return INSTANCE_TYPES_ROOT;
  }
}

export function isBlueprintCatalogRoot(path: string): path is BlueprintCatalogRoot {
  return (BLUEPRINT_CATALOG_ROOTS as readonly string[]).includes(path);
}

export function blueprintNameFromPath(path: string): string | null {
  for (const root of BLUEPRINT_CATALOG_ROOTS) {
    const prefix = `${root}.`;
    if (path.startsWith(prefix)) {
      return path.slice(prefix.length);
    }
  }
  return null;
}

export function isBlueprintsPath(path: string): boolean {
  if (BLUEPRINT_CATALOG_ROOTS.some((root) => path === root || path.startsWith(`${root}.`))) {
    return true;
  }
  return false;
}

export function blueprintCatalogRootFromPath(path: string): BlueprintCatalogRoot | null {
  for (const root of BLUEPRINT_CATALOG_ROOTS) {
    if (path === root || path.startsWith(`${root}.`)) {
      return root;
    }
  }
  return null;
}
