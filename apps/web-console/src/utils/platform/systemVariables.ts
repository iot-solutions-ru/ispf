/** Platform-managed variables — edited on other tabs, not in Variables list. */
export const HIDDEN_OBJECT_VARIABLES = new Set([
  "@historianRuleMeta",
  "@bindingRules",
  "uiIcon",
]);

/** DEVICE driver bookkeeping — must not be deleted from the Variables tab. */
export const DRIVER_SYSTEM_VARIABLES = new Set([
  "driverId",
  "driverPollIntervalMs",
  "driverConfigJson",
  "driverPointMappingsJson",
  "driverStatus",
]);

export function isHiddenObjectVariable(name: string): boolean {
  return HIDDEN_OBJECT_VARIABLES.has(name);
}

/**
 * User-created variables may be deleted from the inspector.
 * Blocks platform/driver bookkeeping and `@…` reserved names.
 */
export function isDeletableUserVariable(name: string): boolean {
  if (!name || name.startsWith("@") || isHiddenObjectVariable(name)) {
    return false;
  }
  return !DRIVER_SYSTEM_VARIABLES.has(name);
}

/** User-facing variable names for pickers (targets, expression context). */
export function filterUserVariableNames(names: readonly string[]): string[] {
  return names.filter((name) => !name.startsWith("@") && !isHiddenObjectVariable(name));
}
