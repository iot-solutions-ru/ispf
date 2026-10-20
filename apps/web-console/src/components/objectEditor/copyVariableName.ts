import type { DataRecord, DataSchema, VariableDto } from "../../types";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";

export function suggestedVariableCopyName(sourceName: string): string {
  const candidate = `${sourceName}Copy`;
  return isTechnicalIdentifier(candidate, "code") ? candidate : "";
}

export function schemaForVariableCopy(variable: VariableDto, name: string): DataSchema | undefined {
  const source = variable.schema ?? variable.value?.schema;
  if (!source) {
    return undefined;
  }
  return { ...source, name };
}

export function valueForVariableCopy(
  variable: VariableDto,
  schema: DataSchema | undefined,
): DataRecord | undefined {
  const value = variable.value;
  if (!value || value.rows.length === 0) {
    return undefined;
  }
  return schema ? { ...value, schema } : value;
}
