/** Convert A1-style address to 0-based row/col (A1 → row 0, col 0). */
export function a1ToRowCol(address: string): { row: number; col: number } | null {
  const match = /^([A-Z]+)(\d+)$/i.exec(address.trim());
  if (!match) {
    return null;
  }
  const colLetters = match[1].toUpperCase();
  const row = Number.parseInt(match[2], 10) - 1;
  if (row < 0) {
    return null;
  }
  const col = columnLettersToIndex(colLetters);
  if (col === null) {
    return null;
  }
  return { row, col };
}

export function columnLettersToIndex(letters: string): number | null {
  const colLetters = letters.replace(/\$/g, "").toUpperCase();
  if (!/^[A-Z]+$/.test(colLetters)) {
    return null;
  }
  let col = 0;
  for (let i = 0; i < colLetters.length; i++) {
    col = col * 26 + (colLetters.charCodeAt(i) - 64);
  }
  return col - 1;
}

export interface SheetBounds {
  rows: number;
  cols: number;
}

export const DEFAULT_SHEET_BOUNDS: SheetBounds = { rows: 500, cols: 52 };

export function resolveRangeEndpoints(
  start: string,
  end: string,
  bounds: SheetBounds,
  flags: {
    startColumnOnly?: boolean;
    endColumnOnly?: boolean;
    startRowOnly?: boolean;
    endRowOnly?: boolean;
  } = {}
): { start: string; end: string } | null {
  if (flags.startColumnOnly && flags.endColumnOnly) {
    const cStart = columnLettersToIndex(start);
    const cEnd = columnLettersToIndex(end);
    if (cStart === null || cEnd === null) {
      return null;
    }
    const minCol = Math.min(cStart, cEnd);
    const maxCol = Math.max(cStart, cEnd);
    return {
      start: rowColToA1(0, minCol),
      end: rowColToA1(bounds.rows - 1, maxCol),
    };
  }

  if (flags.startRowOnly && flags.endRowOnly) {
    const rStart = Number.parseInt(start, 10) - 1;
    const rEnd = Number.parseInt(end, 10) - 1;
    if (rStart < 0 || rEnd < 0) {
      return null;
    }
    const minRow = Math.min(rStart, rEnd);
    const maxRow = Math.max(rStart, rEnd);
    return {
      start: rowColToA1(minRow, 0),
      end: rowColToA1(maxRow, bounds.cols - 1),
    };
  }

  const a = a1ToRowCol(start);
  const b = a1ToRowCol(end);
  if (!a || !b) {
    return null;
  }
  return { start: start.toUpperCase(), end: end.toUpperCase() };
}

/** Convert 0-based row/col to A1 address. */
export function rowColToA1(row: number, col: number): string {
  let n = col + 1;
  let letters = "";
  while (n > 0) {
    const rem = (n - 1) % 26;
    letters = String.fromCharCode(65 + rem) + letters;
    n = Math.floor((n - 1) / 26);
  }
  return `${letters}${row + 1}`;
}

export function defaultColLabels(cols: number): string[] {
  return Array.from({ length: cols }, (_, i) => rowColToA1(0, i).replace(/\d+$/, ""));
}
