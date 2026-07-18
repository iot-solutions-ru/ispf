/** Simple client-side payload filter: `count>10`, `name contains abc`, joined with `&&`. */
export function matchesPayloadFilter(
  row: Record<string, unknown> | undefined,
  expr: string | undefined
): boolean {
  if (!expr?.trim()) {
    return true;
  }
  if (!row) {
    return false;
  }
  const orParts = expr.split(/\s*\|\|\s*/).map((part) => part.trim()).filter(Boolean);
  return orParts.some((orPart) => {
    const andParts = orPart.split(/\s*&&\s*|\s*;\s*/).map((part) => part.trim()).filter(Boolean);
    return andParts.every((part) => evaluateCondition(row, part));
  });
}

function evaluateCondition(row: Record<string, unknown>, condition: string): boolean {
  const containsMatch = condition.match(/^(\w+)\s+contains\s+(.+)$/i);
  if (containsMatch) {
    const left = String(row[containsMatch[1]] ?? "");
    const needle = containsMatch[2].trim().replace(/^['"]|['"]$/g, "");
    return left.toLowerCase().includes(needle.toLowerCase());
  }

  const cmpMatch = condition.match(/^(\w+)\s*(>=|<=|!=|==|>|<|=)\s*(.+)$/);
  if (cmpMatch) {
    const [, field, op, rawRight] = cmpMatch;
    const left = row[field];
    const rightRaw = rawRight.trim().replace(/^['"]|['"]$/g, "");

    if (typeof left === "number" || (!Number.isNaN(Number(left)) && left !== "" && left != null)) {
      const leftNum = Number(left);
      const rightNum = Number(rightRaw);
      if (!Number.isFinite(leftNum) || !Number.isFinite(rightNum)) {
        return false;
      }
      return compareNumbers(leftNum, op, rightNum);
    }

    const leftStr = String(left ?? "");
    if (op === "==" || op === "=") {
      return leftStr === rightRaw;
    }
    if (op === "!=") {
      return leftStr !== rightRaw;
    }
    return false;
  }

  return true;
}

function compareNumbers(left: number, op: string, right: number): boolean {
  switch (op) {
    case ">":
      return left > right;
    case ">=":
      return left >= right;
    case "<":
      return left < right;
    case "<=":
      return left <= right;
    case "==":
    case "=":
      return left === right;
    case "!=":
      return left !== right;
    default:
      return false;
  }
}
