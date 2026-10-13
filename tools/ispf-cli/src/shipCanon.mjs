/**
 * Offline ship-canon checks for `ispf validate --strict` / marketplace catalog (ADR-0060 Wave 1A).
 * Mirrors BundleManifestValidator LOGIC_HOST_DEVICE promotion (no live tree).
 */

function resolveObjectPath(parentPath, name) {
  const parent = (parentPath || "").replace(/\.$/, "");
  const segment = (name || "").trim();
  if (!parent) return segment;
  if (!segment) return parent;
  return `${parent}.${segment}`;
}

/**
 * @param {object} manifest
 * @returns {{ code: string, path: string, message: string }[]}
 */
export function findLogicHostDeviceIssues(manifest) {
  const pathTypes = new Map();
  const issues = [];

  for (const obj of manifest?.objects ?? []) {
    if (!obj || typeof obj !== "object") continue;
    const full = resolveObjectPath(obj.parentPath, obj.name);
    if (full && obj.type) {
      pathTypes.set(full, String(obj.type).toUpperCase());
    }
  }

  for (const bp of manifest?.blueprints ?? []) {
    if (!bp || typeof bp !== "object") continue;
    const name = String(bp.name || "").trim();
    if (!name) continue;
    const type = String(bp.type || "").toUpperCase();
    // SINGLETON hubs land under singleton-blueprints; INSTANCE under instance types — unknown path here.
    if (type === "SINGLETON" && String(bp.targetObjectType || "").toUpperCase() === "DEVICE") {
      issues.push({
        code: "LOGIC_HOST_DEVICE",
        path: `blueprints[${manifest.blueprints.indexOf(bp)}].targetObjectType`,
        message: `SINGLETON blueprint '${name}' uses targetObjectType=DEVICE`,
      });
    }
  }

  (manifest?.functions ?? []).forEach((fn, index) => {
    const objectPath = String(fn?.objectPath || "").trim();
    if (!objectPath) return;
    const hostType = pathTypes.get(objectPath);
    if (hostType === "DEVICE") {
      issues.push({
        code: "LOGIC_HOST_DEVICE",
        path: `functions[${index}].objectPath`,
        message: `Function '${fn.functionName ?? "?"}' is hosted on DEVICE path '${objectPath}'`,
      });
    }
  });

  return issues;
}

/**
 * Apply --strict: promote LOGIC_HOST_DEVICE into local validate errors.
 * @returns {{ status: string, errors: string[], warnings: string[], shipCanon?: object[] }}
 */
export function applyStrictShipCanon(localResult, manifest) {
  const shipIssues = findLogicHostDeviceIssues(manifest);
  if (shipIssues.length === 0) {
    return localResult;
  }
  const errors = [...(localResult.errors ?? [])];
  for (const issue of shipIssues) {
    errors.push(`[${issue.code}] ${issue.path}: ${issue.message}`);
  }
  return {
    ...localResult,
    status: "ERROR",
    errors,
    shipCanon: shipIssues,
  };
}
