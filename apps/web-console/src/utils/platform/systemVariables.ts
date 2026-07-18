/** Platform-managed variables — edited on other tabs, not in Variables list. */
export const HIDDEN_OBJECT_VARIABLES = new Set([
  "@historianRuleMeta",
  "@bindingRules",
  "uiIcon",
]);

export function isHiddenObjectVariable(name: string): boolean {
  return HIDDEN_OBJECT_VARIABLES.has(name);
}

/** User-facing variable names for pickers (targets, expression context). */
export function filterUserVariableNames(names: readonly string[]): string[] {
  return names.filter((name) => !name.startsWith("@") && !isHiddenObjectVariable(name));
}
