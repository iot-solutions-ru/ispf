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

function hostType(pathTypes, objectPath) {
  if (!objectPath) return undefined;
  if (objectPath.startsWith("root.platform.singleton-blueprints.")) {
    const mapped = pathTypes.get(objectPath);
    if (mapped && mapped !== "DEVICE") return mapped;
    return "CUSTOM";
  }
  return pathTypes.get(objectPath);
}

function pushDeviceHostIssue(issues, path, message) {
  issues.push({
    code: "LOGIC_HOST_DEVICE",
    path,
    message,
  });
}

function blueprintViolatesLogicHostCanon(bp) {
  const target = String(bp.targetObjectType || "").toUpperCase();
  if (target !== "DEVICE") return false;
  const type = String(bp.type || "").toUpperCase();
  if (type === "SINGLETON") return true;
  if (type === "INSTANCE") {
    return Array.isArray(bp.functions) && bp.functions.length > 0;
  }
  return false;
}

function buildDeclaredFunctionHosts(manifest) {
  const hosts = new Map();
  for (const fn of manifest?.functions ?? []) {
    const name = String(fn?.functionName || "").trim();
    const objectPath = String(fn?.objectPath || "").trim();
    if (name && objectPath && !hosts.has(name)) {
      hosts.set(name, objectPath);
    }
  }
  return hosts;
}

function warnSplitBrainInvoke(issues, declaredHosts, pathTypes, invokePath, functionName, issuePath, siteLabel) {
  const path = String(invokePath || "").trim();
  const fn = String(functionName || "").trim();
  if (!path || !fn) return;
  const canonical = declaredHosts.get(fn);
  if (!canonical || canonical === path) return;
  if (hostType(pathTypes, path) !== "DEVICE") return;
  pushDeviceHostIssue(
    issues,
    issuePath,
    `${siteLabel} invokes function '${fn}' on DEVICE path '${path}' but functions[] declares it on '${canonical}'`,
  );
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
    if (blueprintViolatesLogicHostCanon(bp)) {
      issues.push({
        code: "LOGIC_HOST_DEVICE",
        path: `blueprints[${manifest.blueprints.indexOf(bp)}].targetObjectType`,
        message: `${String(bp.type || "").toUpperCase()} blueprint '${name}' uses targetObjectType=DEVICE`,
      });
    }
    const type = String(bp.type || "").toUpperCase();
    if (type === "SINGLETON") {
      const singletonPath = `root.platform.singleton-blueprints.${name}`;
      const target = String(bp.targetObjectType || "CUSTOM").toUpperCase();
      pathTypes.set(singletonPath, target === "DEVICE" ? "DEVICE" : "CUSTOM");
    }
  }

  const declaredHosts = buildDeclaredFunctionHosts(manifest);

  (manifest?.functions ?? []).forEach((fn, index) => {
    const objectPath = String(fn?.objectPath || "").trim();
    if (!objectPath) return;
    if (hostType(pathTypes, objectPath) === "DEVICE") {
      pushDeviceHostIssue(
        issues,
        `functions[${index}].objectPath`,
        `Function '${fn.functionName ?? "?"}' is hosted on DEVICE path '${objectPath}'`,
      );
    }
  });

  (manifest?.dashboards ?? []).forEach((dash, dashIndex) => {
    let layout;
    try {
      layout = JSON.parse(dash?.layoutJson ?? "{}");
    } catch {
      return;
    }
    (layout.widgets ?? []).forEach((widget, widgetIndex) => {
      const type = String(widget?.type || "");
      if (type !== "function" && type !== "function-form") return;
      warnSplitBrainInvoke(
        issues,
        declaredHosts,
        pathTypes,
        widget?.objectPath,
        widget?.functionName,
        `dashboards[${dashIndex}].widgets[${widgetIndex}].objectPath`,
        "Dashboard widget",
      );
    });
  });

  (manifest?.schedules ?? []).forEach((schedule, index) => {
    if (String(schedule?.actionType || "").toLowerCase() !== "invoke_function") return;
    warnSplitBrainInvoke(
      issues,
      declaredHosts,
      pathTypes,
      schedule?.action?.objectPath,
      schedule?.action?.functionName,
      `schedules[${index}].action.objectPath`,
      `Schedule '${schedule.scheduleId ?? index}'`,
    );
  });

  const bpmnInvoke = /ispf:action="invoke_function"[\s\S]*?ispf:objectPath="([^"]+)"[\s\S]*?ispf:functionName="([^"]+)"/g;
  (manifest?.workflows ?? []).forEach((workflow, index) => {
    const xml = String(workflow?.bpmnXml || "");
    let match;
    let taskIndex = 0;
    while ((match = bpmnInvoke.exec(xml)) !== null) {
      warnSplitBrainInvoke(
        issues,
        declaredHosts,
        pathTypes,
        match[1],
        match[2],
        `workflows[${index}].bpmnXml.serviceTask[${taskIndex}].objectPath`,
        "Workflow BPMN",
      );
      taskIndex++;
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
