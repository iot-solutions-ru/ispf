export const OPERATOR_APPS_ROOT = "root.platform.operator-apps";

/** Visual group node name prefix for bundle catalog folders (see BundleVisualGroupService). */
export const BUNDLE_VISUAL_GROUP_PREFIX = "bundle-";

export function isOperatorAppsRootPath(path: string): boolean {
  return path === OPERATOR_APPS_ROOT;
}

export function isOperatorAppChildPath(path: string): boolean {
  return path.startsWith(`${OPERATOR_APPS_ROOT}.`);
}

export function isOperatorAppsPath(path: string): boolean {
  return isOperatorAppsRootPath(path) || isOperatorAppChildPath(path);
}

/** Leaf segment under Operator Apps (sanitized app id in tree). */
export function operatorAppLeafFromPath(path: string): string | null {
  if (!isOperatorAppChildPath(path)) {
    return null;
  }
  return path.slice(OPERATOR_APPS_ROOT.length + 1);
}

export function operatorAppIdCandidates(pathLeaf: string): string[] {
  const candidates = [pathLeaf];
  if (pathLeaf.startsWith(BUNDLE_VISUAL_GROUP_PREFIX)) {
    candidates.push(pathLeaf.slice(BUNDLE_VISUAL_GROUP_PREFIX.length));
  }
  return candidates;
}

export function resolveOperatorAppId(
  pathLeaf: string,
  apps: { appId: string }[]
): string {
  for (const candidate of operatorAppIdCandidates(pathLeaf)) {
    const exact = apps.find((app) => app.appId === candidate);
    if (exact) {
      return exact.appId;
    }
    const sanitized = apps.find(
      (app) => app.appId.replace(/[^a-zA-Z0-9_-]/g, "_") === candidate,
    );
    if (sanitized) {
      return sanitized.appId;
    }
  }
  return pathLeaf;
}

/** Resolve registry app id from an operator-apps tree path. */
export function resolveOperatorAppIdFromPath(
  path: string,
  apps: { appId: string }[] = [],
): string | null {
  const leaf = operatorAppLeafFromPath(path);
  if (!leaf) {
    return null;
  }
  return resolveOperatorAppId(leaf, apps);
}
