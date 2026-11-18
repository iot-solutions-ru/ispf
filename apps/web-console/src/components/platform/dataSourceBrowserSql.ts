export const COLUMN_TYPES = [
  "INTEGER",
  "BIGINT",
  "BOOLEAN",
  "TEXT",
  "VARCHAR",
  "NUMERIC",
  "DOUBLE",
  "DATE",
  "TIMESTAMP",
  "UUID",
] as const;

export type ColumnTypeName = (typeof COLUMN_TYPES)[number];

const IDENT = /^[A-Za-z_][A-Za-z0-9_]{0,62}$/;

export function isSqlIdent(value: string): boolean {
  return IDENT.test(value);
}

export function quoteIdent(name: string, quote = '"'): string {
  if (!name || hasControlChar(name)) {
    throw new Error("Invalid identifier");
  }
  if (quote === "[") {
    if (name.includes("]")) {
      throw new Error("Invalid identifier");
    }
    return `[${name}]`;
  }
  const mark = quote || '"';
  return mark + name.split(mark).join(mark + mark) + mark;
}

/** Portable single-table read. Row cap is applied by the execute-query API, not a vendor LIMIT. */
export function previewSelect(qualifier: string | null, table: string, quote = '"'): string {
  const tableSql = quoteIdent(table, quote);
  if (qualifier && qualifier.length > 0) {
    return `SELECT * FROM ${quoteIdent(qualifier, quote)}.${tableSql}`;
  }
  return `SELECT * FROM ${tableSql}`;
}

export function tableNodeKey(
  schemaName: string,
  catalog: string | null,
  schema: string | null,
  table: string,
): string {
  return ["table", enc(schemaName), enc(catalog ?? ""), enc(schema ?? ""), enc(table)].join("|");
}

export function parseTableNodeKey(key: string): {
  schemaName: string;
  catalog: string | null;
  schema: string | null;
  table: string;
} | null {
  const parts = key.split("|");
  if (parts.length !== 5 || parts[0] !== "table") {
    return null;
  }
  return {
    schemaName: dec(parts[1]),
    catalog: dec(parts[2]) || null,
    schema: dec(parts[3]) || null,
    table: dec(parts[4]),
  };
}

export function rowsToCsv(columns: string[], rows: Array<Record<string, unknown>>): string {
  const lines = [columns.map(csvCell).join(",")];
  for (const row of rows) {
    lines.push(columns.map((column) => csvCell(row[column])).join(","));
  }
  return lines.join("\n");
}

export function downloadCsv(filename: string, columns: string[], rows: Array<Record<string, unknown>>): void {
  const blob = new Blob([rowsToCsv(columns, rows)], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function csvCell(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  const text = typeof value === "object" ? JSON.stringify(value) : String(value);
  if (/[",\n\r]/.test(text)) {
    return `"${text.replaceAll('"', '""')}"`;
  }
  return text;
}

function hasControlChar(name: string): boolean {
  for (let index = 0; index < name.length; index += 1) {
    if (name.charCodeAt(index) < 32) {
      return true;
    }
  }
  return false;
}

function enc(value: string): string {
  return encodeURIComponent(value);
}

function dec(value: string): string {
  return decodeURIComponent(value);
}
