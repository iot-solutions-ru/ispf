import type { VariableDto } from "../types";

export function variableString(variables: VariableDto[], name: string): string {
  const variable = variables.find((item) => item.name === name);
  const value = variable?.value?.rows?.[0]?.value;
  return value != null ? String(value) : "";
}

export function variableBoolean(variables: VariableDto[], name: string, fallback = false): boolean {
  const variable = variables.find((item) => item.name === name);
  const value = variable?.value?.rows?.[0]?.value;
  if (typeof value === "boolean") {
    return value;
  }
  if (value == null) {
    return fallback;
  }
  return String(value) === "true";
}

export function variableNumber(variables: VariableDto[], name: string, fallback = 0): number {
  const variable = variables.find((item) => item.name === name);
  const value = variable?.value?.rows?.[0]?.value;
  if (typeof value === "number") {
    return value;
  }
  if (value == null) {
    return fallback;
  }
  return Number(value);
}
