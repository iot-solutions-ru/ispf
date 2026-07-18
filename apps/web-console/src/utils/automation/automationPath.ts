export const ALERT_RULES_ROOT = "root.platform.alert-rules";
export const CORRELATORS_ROOT = "root.platform.correlators";

export function isAlertRulesRoot(path: string): boolean {
  return path === ALERT_RULES_ROOT;
}

export function isCorrelatorsRoot(path: string): boolean {
  return path === CORRELATORS_ROOT;
}

export function isAlertRulePath(path: string): boolean {
  return path.startsWith(`${ALERT_RULES_ROOT}.`);
}

export function isCorrelatorPath(path: string): boolean {
  return path.startsWith(`${CORRELATORS_ROOT}.`);
}
