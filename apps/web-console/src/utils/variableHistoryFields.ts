import type { VariableDto } from "../types";

const NUMERIC_FIELD_TYPES = new Set([
  "DOUBLE",
  "FLOAT",
  "INTEGER",
  "INT",
  "LONG",
  "SHORT",
  "NUMBER",
  "BOOLEAN",
]);

/** Schema fields that the historian can sample (matches server numeric coercion). */
export function historizableFieldsFromVariable(variable: VariableDto): string[] {
  const schemaFields = variable.value?.schema?.fields ?? [];
  const numeric = schemaFields
    .filter((field) => NUMERIC_FIELD_TYPES.has(field.type.toUpperCase()))
    .map((field) => field.name);
  if (numeric.length > 0) {
    return numeric;
  }
  if (schemaFields.some((field) => field.name === "value")) {
    return ["value"];
  }
  return schemaFields.length > 0 ? [schemaFields[0].name] : ["value"];
}

export function defaultHistorizableField(variable: VariableDto): string {
  const fields = historizableFieldsFromVariable(variable);
  return fields.includes("value") ? "value" : fields[0];
}
