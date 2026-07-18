import type { TFunction } from "i18next";

const MISSING = "\0missing\0";

/** Localize built-in / template role descriptions when i18n keys exist. */
export function localizedRoleDescription(
  t: TFunction,
  roleName: string,
  serverDescription?: string,
): string {
  const key = `security:role.builtin.${roleName}.description`;
  const value = t(key, { defaultValue: MISSING });
  if (value !== MISSING) {
    return value;
  }
  return (serverDescription ?? "").trim();
}
