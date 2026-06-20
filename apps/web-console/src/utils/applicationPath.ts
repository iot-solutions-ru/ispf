import { APPLICATIONS_ROOT } from "./createObjectMode";

/** Extract appId from APPLICATION object description (`appId=wh, schema=...`). */
export function appIdFromApplicationDescription(description: string | undefined): string | null {
  if (!description) {
    return null;
  }
  const match = description.match(/appId=([^,\s]+)/);
  return match?.[1] ?? null;
}

export function isApplicationObjectPath(path: string): boolean {
  return path.startsWith(`${APPLICATIONS_ROOT}.`)
    && !path.endsWith(".reports")
    && !path.endsWith(".functions")
    && !path.endsWith(".schedules")
    && !path.endsWith(".bindings")
    && !path.endsWith(".migrations")
    && !path.endsWith(".screens")
    && path.split(".").length === APPLICATIONS_ROOT.split(".").length + 1;
}

export function resolveApplicationAppId(path: string, description?: string): string | null {
  const fromDescription = appIdFromApplicationDescription(description);
  if (fromDescription) {
    return fromDescription;
  }
  if (!isApplicationObjectPath(path)) {
    return null;
  }
  return path.slice(APPLICATIONS_ROOT.length + 1);
}
