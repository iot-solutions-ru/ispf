export interface DefaultApplicationManifestOptions {
  appId: string;
  displayName?: string;
  schemaName?: string;
}

export function defaultApplicationManifestObject({
  appId,
  displayName,
  schemaName,
}: DefaultApplicationManifestOptions): Record<string, unknown> {
  const safeSchema =
    schemaName?.trim()
    || `app_${appId.replace(/[^a-zA-Z0-9_]/g, "_")}`;
  return {
    version: "1.0.0",
    displayName: displayName?.trim() || appId,
    schemaName: safeSchema,
    tablePrefix: "",
    objects: [],
    dashboards: [],
    workflows: [],
    reports: [],
    functions: [],
    bindings: [],
    alertRules: [],
    correlators: [],
    schedules: [],
    migrations: [],
    events: [],
  };
}

export function defaultApplicationManifestText(options: DefaultApplicationManifestOptions): string {
  return JSON.stringify(defaultApplicationManifestObject(options), null, 2);
}
