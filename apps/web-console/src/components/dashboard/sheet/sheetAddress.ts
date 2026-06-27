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
  let col = 0;
  for (let i = 0; i < colLetters.length; i++) {
    col = col * 26 + (colLetters.charCodeAt(i) - 64);
  }
  return { row, col: col - 1 };
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
