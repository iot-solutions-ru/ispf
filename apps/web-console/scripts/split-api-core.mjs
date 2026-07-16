import fs from "node:fs";

const src = fs.readFileSync("src/api.ts", "utf8");
const lines = src.split(/\r?\n/);

const httpClient = `import { getAuthHeaders, getStoredSession } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";
import { invalidateStoredSession } from "../auth/validateSession";
import { fetchWithIngressFallback } from "../utils/ingressFetch";

${lines.slice(20, 110).join("\n")}

export type { ObjectWriteOptions };
`;

const objectsCore = `import type {
  ObjectSummary,
  CreateObjectPayload,
  DataRecord,
  DataSchema,
  UpdateObjectPayload,
  VariableDto,
  ObjectEditorDto,
  FunctionDescriptor,
  EventDescriptor,
  BindingRule,
} from "../types";
import { getAuthHeaders } from "../auth/session";
import { fetchWithIngressFallback } from "../utils/ingressFetch";
import { request, writeHeaders, type ObjectWriteOptions } from "./httpClient";

${lines.slice(167, 714).join("\n")}
`;

const dashboardsCore = `import type { DashboardView } from "../types/dashboard";
import type { WorkflowLifecycleStatus, WorkflowView } from "../types/workflow";
import { request } from "./httpClient";

${lines
  .slice(714, 848)
  .join("\n")
  .replaceAll('import("./utils/dashboardContext")', 'import("../utils/dashboardContext")')
  .replaceAll('import("./types/dashboard")', 'import("../types/dashboard")')}
`;

// Collect export names from moved slices for the shim.
function exportNames(slice) {
  const names = [];
  for (const line of slice.split("\n")) {
    const m = line.match(/^export (?:async )?function (\w+)/);
    if (m) names.push(m[1]);
    const t = line.match(/^export (?:type|interface) (\w+)/);
    if (t) names.push(t[1]);
  }
  return names;
}

const objectExports = exportNames(lines.slice(167, 714).join("\n"));
const dashExports = exportNames(lines.slice(714, 848).join("\n"));

const remaining = lines
  .slice(848)
  .join("\n")
  .replaceAll('import("./types/', 'import("./types/') // keep relative from api.ts root
  ;

const apiShim = `import type { PlatformInfo } from "./types";
import { request } from "./api/httpClient";
import { getAuthHeaders } from "./auth/session";
import { fetchWithIngressFallback } from "./utils/ingressFetch";

export { writeHeaders, type ObjectWriteOptions } from "./api/httpClient";

export type {
${objectExports
  .filter((n) => /^[A-Z]/.test(n))
  .map((n) => `  ${n},`)
  .join("\n")}
} from "./api/objectsCore";

export {
${objectExports
  .filter((n) => !/^[A-Z]/.test(n))
  .map((n) => `  ${n},`)
  .join("\n")}
} from "./api/objectsCore";

export type {
${dashExports
  .filter((n) => /^[A-Z]/.test(n))
  .map((n) => `  ${n},`)
  .join("\n")}
} from "./api/dashboardsCore";

export {
${dashExports
  .filter((n) => !/^[A-Z]/.test(n))
  .map((n) => `  ${n},`)
  .join("\n")}
} from "./api/dashboardsCore";

export function fetchPlatformInfo(): Promise<PlatformInfo> {
  return request("/api/v1/info");
}

export interface AuthMe {
  authenticated: boolean;
  principal?: string;
  roles: string[];
  timeZone?: string;
}

export function fetchAuthMe(): Promise<AuthMe> {
  return request("/api/v1/auth/me");
}

export function updateAuthTimeZone(timeZone: string): Promise<{ username: string; timeZone: string }> {
  return request("/api/v1/auth/me/timezone", {
    method: "PUT",
    body: JSON.stringify({ timeZone }),
  });
}

export function validateExpression(expression: string): Promise<{ valid: boolean; expression: string; error: string | null }> {
  return request("/api/v1/expressions/validate", {
    method: "POST",
    body: JSON.stringify({ expression }),
  });
}

export interface EvaluateExpressionPayload {
  objectPath: string;
  expression: string;
  targetVariable?: string;
}

export interface EvaluateExpressionStep {
  phase: string;
  status: string;
  detail?: unknown;
}

export function evaluateExpression(
  payload: EvaluateExpressionPayload
): Promise<{
  valid: boolean;
  expression: string;
  result: unknown;
  resultType: string | null;
  error: string | null;
  steps: EvaluateExpressionStep[];
}> {
  return request("/api/v1/expressions/evaluate", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

${remaining}
`;

fs.writeFileSync("src/api/httpClient.ts", httpClient);
fs.writeFileSync("src/api/objectsCore.ts", objectsCore);
fs.writeFileSync("src/api/dashboardsCore.ts", dashboardsCore);
fs.writeFileSync("src/api.ts", apiShim);
console.log("split ok", { objectExports: objectExports.length, dashExports: dashExports.length });
