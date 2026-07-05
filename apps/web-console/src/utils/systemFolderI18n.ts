import type { TFunction } from "i18next";

const APPLICATIONS_PREFIX = "root.platform.applications.";
const OPERATOR_APPS_PREFIX = "root.platform.operator-apps.";
const FEDERATION_PREFIX = "root.platform.federation.";

export function objectTreePathKey(path: string): string {
  return path.replace(/\./g, "__");
}

type I18nLookup =
  | { kind: "path"; path: string }
  | { kind: "pattern"; pattern: string; vars?: Record<string, string> };

export function resolveObjectTreeLookup(path: string): I18nLookup | null {
  if (path.startsWith(APPLICATIONS_PREFIX)) {
    const rest = path.slice(APPLICATIONS_PREFIX.length);
    if (!rest) {
      return null;
    }
    if (!rest.includes(".")) {
      return { kind: "pattern", pattern: "applicationRoot" };
    }
    for (const suffix of [
      "functions",
      "reports",
      "schedules",
      "bindings",
      "migrations",
      "screens",
    ] as const) {
      if (rest.endsWith(`.${suffix}`)) {
        return { kind: "pattern", pattern: `application${suffix.charAt(0).toUpperCase()}${suffix.slice(1)}` };
      }
    }
  }

  if (path.startsWith(OPERATOR_APPS_PREFIX)) {
    const rest = path.slice(OPERATOR_APPS_PREFIX.length);
    if (rest && !rest.includes(".")) {
      return { kind: "pattern", pattern: "operatorApp" };
    }
  }

  if (path.startsWith(FEDERATION_PREFIX)) {
    const rest = path.slice(FEDERATION_PREFIX.length);
    if (rest && !rest.includes(".")) {
      return { kind: "pattern", pattern: "federationPeer", vars: { peerName: rest } };
    }
  }

  return { kind: "path", path: objectTreePathKey(path) };
}

function lookupTitle(t: TFunction, lookup: I18nLookup, displayName?: string): string {
  if (lookup.kind === "path") {
    const key = `objectTree:path.${lookup.path}.title`;
    const value = t(key, { defaultValue: "" });
    if (value) {
      return value;
    }
  } else {
    const titleKey = `objectTree:pattern.${lookup.pattern}.title`;
    const value = t(titleKey, { defaultValue: "", ...lookup.vars });
    if (value) {
      return value;
    }
  }
  return displayName?.trim() ?? "";
}

function lookupDescription(t: TFunction, lookup: I18nLookup, serverDescription?: string): string {
  if (lookup.kind === "path") {
    const key = `objectTree:path.${lookup.path}.description`;
    const value = t(key, { defaultValue: "" });
    if (value) {
      return value;
    }
  } else {
    const key = `objectTree:pattern.${lookup.pattern}.description`;
    const value = t(key, { defaultValue: "", ...lookup.vars });
    if (value) {
      return value;
    }
  }
  return serverDescription?.trim() ?? "";
}

function lookupIdColumn(t: TFunction, lookup: I18nLookup, fallback: string): string {
  if (lookup.kind === "path") {
    const key = `objectTree:path.${lookup.path}.idColumnLabel`;
    const value = t(key, { defaultValue: "" });
    if (value) {
      return value;
    }
  }
  return t(`objectTree:idColumn.${fallback}`, { defaultValue: fallback });
}

export function localizedSystemObjectDescription(
  t: TFunction,
  path: string,
  serverDescription?: string,
): string {
  const lookup = resolveObjectTreeLookup(path);
  if (!lookup) {
    return serverDescription?.trim() ?? "";
  }
  const localized = lookupDescription(t, lookup, serverDescription);
  if (localized) {
    return localized;
  }
  return (
    t("objectTree:fallback.description", { path, defaultValue: "" })
    || serverDescription?.trim()
    || ""
  );
}

export function localizedSystemFolderMeta(
  t: TFunction,
  path: string,
  displayName?: string,
  serverDescription?: string,
  idColumnFallback = "id",
): { title: string; description: string; idColumnLabel: string } {
  const lookup = resolveObjectTreeLookup(path);
  if (!lookup) {
    return {
      title: displayName?.trim() || path.split(".").pop() || path,
      description:
        t("objectTree:fallback.description", { path })
        || serverDescription?.trim()
        || "",
      idColumnLabel: t(`objectTree:idColumn.${idColumnFallback}`),
    };
  }

  const title = lookupTitle(t, lookup, displayName)
    || displayName?.trim()
    || path.split(".").pop()
    || path;
  const description = lookupDescription(t, lookup, serverDescription)
    || t("objectTree:fallback.description", { path })
    || serverDescription?.trim()
    || "";
  const idColumnLabel = lookupIdColumn(t, lookup, idColumnFallback);

  return { title, description, idColumnLabel };
}
