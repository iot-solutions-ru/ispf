import type { OperatorManifestAction, OperatorManifestField } from "../types/operatorManifest";
import { isActionVisible } from "./manifestVisibility";
import { toBffInput, type BffFieldType } from "./bff";

function fieldType(field: OperatorManifestField, raw: unknown): BffFieldType {
  if (field.type) {
    return field.type === "SELECT" || field.type === "TEXT" ? "STRING" : field.type;
  }
  if (typeof raw === "boolean") {
    return "BOOLEAN";
  }
  if (typeof raw === "number") {
    return Number.isInteger(raw) ? "LONG" : "DOUBLE";
  }
  return "STRING";
}

function parseValue(field: OperatorManifestField, raw: unknown): unknown {
  const type = fieldType(field, raw);
  if (raw === "" || raw === null || raw === undefined) {
    return undefined;
  }
  switch (type) {
    case "BOOLEAN":
      return raw === true || raw === "true" || raw === 1 || raw === "1";
    case "DOUBLE":
      return typeof raw === "number" ? raw : Number.parseFloat(String(raw));
    case "LONG":
    case "INTEGER":
      return typeof raw === "number" ? Math.trunc(raw) : Number.parseInt(String(raw), 10);
    default:
      return String(raw);
  }
}

export function buildActionInput(
  action: OperatorManifestAction,
  formValues: Record<string, unknown>,
  selectedRow: Record<string, unknown> | null
): Record<string, unknown> {
  const input: Record<string, unknown> = { ...(action.input ?? {}) };

  for (const field of action.fields ?? []) {
    let value: unknown;
    if (field.bindFromSelection && selectedRow) {
      value = selectedRow[field.bindFromSelection];
    } else if (formValues[field.name] !== undefined && formValues[field.name] !== "") {
      value = formValues[field.name];
    } else if (field.default !== undefined) {
      value = field.default;
    } else {
      continue;
    }
    const parsed = parseValue(field, value);
    if (parsed !== undefined) {
      input[field.name] = parsed;
    }
  }

  return input;
}

export function validateActionInput(
  action: OperatorManifestAction,
  formValues: Record<string, unknown>,
  selectedRow: Record<string, unknown> | null
): string | null {
  if (action.requiresSelection && !selectedRow) {
    return "Выберите строку в таблице.";
  }

  if (!isActionVisible(action, selectedRow)) {
    return "Действие недоступно для выбранной строки.";
  }

  for (const field of action.fields ?? []) {
    if (field.hidden || field.bindFromSelection) {
      continue;
    }
    const raw = formValues[field.name];
    if (field.required && (raw === undefined || raw === "")) {
      return `Заполните поле «${field.label ?? field.name}».`;
    }
  }

  return null;
}

export function invokeInputFromAction(
  action: OperatorManifestAction,
  formValues: Record<string, unknown>,
  selectedRow: Record<string, unknown> | null
) {
  const values = buildActionInput(action, formValues, selectedRow);
  const fieldMeta: Record<string, BffFieldType> = {};
  for (const field of action.fields ?? []) {
    if (values[field.name] !== undefined) {
      fieldMeta[field.name] = fieldType(field, values[field.name]);
    }
  }
  for (const [name, value] of Object.entries(values)) {
    if (!fieldMeta[name]) {
      fieldMeta[name] = typeof value === "number" ? (Number.isInteger(value) ? "LONG" : "DOUBLE") : "STRING";
    }
  }
  return toBffInput(values, fieldMeta);
}
