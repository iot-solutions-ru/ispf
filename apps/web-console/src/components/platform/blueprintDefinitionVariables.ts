import type { BlueprintVariableDefinition } from "../../types/blueprints";
import type { VariableDto } from "../../types";
import { BLUEPRINT_CATALOG_META_VARIABLES } from "../../utils/platform/systemVariables";

export { BLUEPRINT_CATALOG_META_VARIABLES };

export function objectVariablesAvailableForDefinition<T extends { name: string }>(
  objectVariables: T[],
  definitionVariables: { name: string }[],
): T[] {
  const taken = new Set(definitionVariables.map((item) => item.name));
  return objectVariables.filter(
    (item) => !BLUEPRINT_CATALOG_META_VARIABLES.has(item.name) && !taken.has(item.name),
  );
}

export function definitionFromObjectVariable(variable: VariableDto): BlueprintVariableDefinition {
  const schema = variable.value?.schema ?? {
    name: variable.name,
    fields: [{ name: "value", type: "STRING" }],
  };
  return {
    name: variable.name,
    description: "",
    group: "default",
    schema,
    readable: variable.readable,
    writable: variable.writable,
    defaultValue: variable.value,
    historyEnabled: variable.historyEnabled,
    historyRetentionDays: variable.historyRetentionDays,
  };
}
