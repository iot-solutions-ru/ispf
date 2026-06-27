import { a1ToRowCol, rowColToA1 } from "./sheetAddress";

export function allCellAddresses(rows: number, cols: number): string[] {
  const result: string[] = [];
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      result.push(rowColToA1(r, c));
    }
  }
  return result;
}

export type NavDirection = "up" | "down" | "left" | "right" | "next" | "prev";

export function moveCellAddress(
  address: string,
  rows: number,
  cols: number,
  direction: NavDirection
): string | null {
  const rc = a1ToRowCol(address);
  if (!rc) {
    return null;
  }
  let { row, col } = rc;
  switch (direction) {
    case "up":
      row = Math.max(0, row - 1);
      break;
    case "down":
      row = Math.min(rows - 1, row + 1);
      break;
    case "left":
      col = Math.max(0, col - 1);
      break;
    case "right":
      col = Math.min(cols - 1, col + 1);
      break;
    case "next": {
      if (col < cols - 1) {
        col += 1;
      } else if (row < rows - 1) {
        row += 1;
        col = 0;
      } else {
        return null;
      }
      break;
    }
    case "prev": {
      if (col > 0) {
        col -= 1;
      } else if (row > 0) {
        row -= 1;
        col = cols - 1;
      } else {
        return null;
      }
      break;
    }
  }
  return rowColToA1(row, col);
}
