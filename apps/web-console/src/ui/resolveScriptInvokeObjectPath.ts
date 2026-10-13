/** Target object path for script `invoke_function` (`self` → caller). */
export function resolveScriptInvokeObjectPath(
  stepObjectPath: string | undefined,
  callerObjectPath: string
): string {
  const raw = (stepObjectPath ?? "").trim();
  if (!raw || raw.toLowerCase() === "self") {
    return callerObjectPath.trim();
  }
  return raw;
}
