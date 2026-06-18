import type { DataRecord, DataSchema, VariableDto } from "../types";

export function emptyRecord(schema: DataSchema): DataRecord {
  const row: Record<string, unknown> = {};
  for (const field of schema.fields) {
    row[field.name] = defaultForType(field.type);
  }
  return { schema, rows: [row] };
}

export function ensureRecord(variable: VariableDto): DataRecord {
  if (variable.value && variable.value.rows.length > 0) {
    return cloneRecord(variable.value);
  }
  return emptyRecord(variable.value?.schema ?? { name: variable.name, fields: [] });
}

export function cloneRecord(record: DataRecord): DataRecord {
  return {
    schema: record.schema,
    rows: record.rows.map((row) => ({ ...row })),
  };
}

export function setFieldValue(
  record: DataRecord,
  fieldName: string,
  raw: unknown
): DataRecord {
  const row = { ...(record.rows[0] ?? {}) };
  const field = record.schema.fields.find((f) => f.name === fieldName);
  row[fieldName] = field ? coerceValue(raw, field.type) : raw;
  return { schema: record.schema, rows: [row] };
}

function defaultForType(type: string): unknown {
  switch (type) {
    case "BOOLEAN":
      return false;
    case "INTEGER":
      return 0;
    case "LONG":
      return 0;
    case "DOUBLE":
      return 0;
    case "STRING":
    case "DATETIME":
      return "";
    default:
      return null;
  }
}

function coerceValue(raw: unknown, type: string): unknown {
  if (raw === "" || raw === null || raw === undefined) {
    return null;
  }
  switch (type) {
    case "BOOLEAN":
      return raw === true || raw === "true";
    case "INTEGER":
      return parseInt(String(raw), 10);
    case "LONG":
      return parseInt(String(raw), 10);
    case "DOUBLE":
      return parseFloat(String(raw));
    case "STRING":
    case "DATETIME":
      return String(raw);
    default:
      return raw;
  }
}

export function recordsEqual(a: DataRecord | null, b: DataRecord | null): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
