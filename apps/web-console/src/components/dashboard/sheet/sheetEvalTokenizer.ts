// Lexer for sheet formulas: numbers, strings, A1 / Sheet!A1 / column / row refs, operators.
import { ERROR } from "./sheetEvalCore";

export type Token =
  | { kind: "num"; value: number }
  | { kind: "str"; value: string }
  | {
      kind: "cell";
      value: string;
      sheet?: string;
      columnOnly?: boolean;
      rowOnly?: boolean;
    }
  | { kind: "ident"; value: string }
  | { kind: "op"; value: string }
  | { kind: "lparen" }
  | { kind: "rparen" }
  | { kind: "comma" }
  | { kind: "colon" }
  | { kind: "eof" };

type RangeEndpoint = {
  value: string;
  columnOnly: boolean;
  rowOnly: boolean;
};

function readRangeEndpoint(input: string, start: number): { endpoint: RangeEndpoint; next: number } | string {
  let i = start;
  let letters = "";
  while (i < input.length && /[A-Z$]/i.test(input[i])) {
    letters += input[i];
    i++;
  }
  let digits = "";
  while (i < input.length && /[0-9]/.test(input[i])) {
    digits += input[i];
    i++;
  }
  const col = letters.replace(/\$/g, "").toUpperCase();
  if (col && digits) {
    return { endpoint: { value: `${col}${digits}`, columnOnly: false, rowOnly: false }, next: i };
  }
  if (col && !digits) {
    return { endpoint: { value: col, columnOnly: true, rowOnly: false }, next: i };
  }
  if (!col && digits) {
    return { endpoint: { value: digits, columnOnly: false, rowOnly: true }, next: i };
  }
  return ERROR.ref;
}

export function tokenize(input: string): Token[] | string {
  const tokens: Token[] = [];
  let i = 0;
  while (i < input.length) {
    const ch = input[i];
    if (/\s/.test(ch)) {
      i++;
      continue;
    }
    if (ch === "(") {
      tokens.push({ kind: "lparen" });
      i++;
      continue;
    }
    if (ch === ")") {
      tokens.push({ kind: "rparen" });
      i++;
      continue;
    }
    if (ch === "," || ch === ";") {
      tokens.push({ kind: "comma" });
      i++;
      continue;
    }
    if (ch === ":") {
      tokens.push({ kind: "colon" });
      i++;
      continue;
    }
    if (ch === '"' || ch === "'") {
      const quote = ch;
      i++;
      let value = "";
      while (i < input.length && input[i] !== quote) {
        if (input[i] === quote && input[i + 1] === quote) {
          value += quote;
          i += 2;
          continue;
        }
        value += input[i];
        i++;
      }
      if (i >= input.length) {
        return ERROR.value;
      }
      i++;
      if (input[i] === "!") {
        i++;
        const cellPart = readRangeEndpoint(input, i);
        if (typeof cellPart === "string") {
          return cellPart;
        }
        i = cellPart.next;
        tokens.push({
          kind: "cell",
          value: cellPart.endpoint.value,
          sheet: value,
          columnOnly: cellPart.endpoint.columnOnly,
          rowOnly: cellPart.endpoint.rowOnly,
        });
        continue;
      }
      tokens.push({ kind: "str", value });
      continue;
    }
    if (ch === "&") {
      tokens.push({ kind: "op", value: "&" });
      i++;
      continue;
    }
    if (">=<=".includes(ch) || "<>".includes(ch) || "+-*/=".includes(ch)) {
      let op = ch;
      const two = input.slice(i, i + 2);
      if (two === ">=" || two === "<=" || two === "<>") {
        op = two;
        i += 2;
      } else {
        i++;
      }
      tokens.push({ kind: "op", value: op });
      continue;
    }
    if (/[0-9.]/.test(ch) || (ch === "-" && /[0-9]/.test(input[i + 1] ?? ""))) {
      let num = ch;
      i++;
      while (i < input.length && /[0-9.]/.test(input[i])) {
        num += input[i];
        i++;
      }
      const parsed = Number.parseFloat(num);
      if (!Number.isFinite(parsed)) {
        return ERROR.value;
      }
      tokens.push({ kind: "num", value: parsed });
      continue;
    }
    if (/[A-Za-z_А-ЯЁа-яё]/.test(ch)) {
      let ident = ch;
      i++;
      while (i < input.length && /[A-Za-z0-9_.А-ЯЁа-яё ]/.test(input[i])) {
        ident += input[i];
        i++;
      }
      if (input[i] === "!") {
        const sheetName = ident.trim();
        i++;
        const cellPart = readRangeEndpoint(input, i);
        if (typeof cellPart === "string") {
          return cellPart;
        }
        i = cellPart.next;
        tokens.push({
          kind: "cell",
          value: cellPart.endpoint.value,
          sheet: sheetName,
          columnOnly: cellPart.endpoint.columnOnly,
          rowOnly: cellPart.endpoint.rowOnly,
        });
        continue;
      }
      const upper = ident.trim().toUpperCase();
      if (/^[A-Z]+\d+$/.test(upper)) {
        tokens.push({ kind: "cell", value: upper });
      } else {
        tokens.push({ kind: "ident", value: upper });
      }
      continue;
    }
    return ERROR.value;
  }
  tokens.push({ kind: "eof" });
  return tokens;
}
