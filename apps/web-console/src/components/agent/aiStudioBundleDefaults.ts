// Extracted from AiStudioBundleTab.tsx so that the component module only exports components
// (react-refresh/only-export-components).

const DEFAULT_MANIFEST = `{
  "version": "1.0.0",
  "displayName": "AI generated app",
  "schemaName": "app_ai_generated",
  "migrations": []
}`;

export function defaultBundleManifest(): string {
  return DEFAULT_MANIFEST;
}
