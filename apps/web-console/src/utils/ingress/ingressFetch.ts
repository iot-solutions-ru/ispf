import { HMI_INGRESS_PREFIX, resolveIngressPath } from "./ingressPath";
import { isOperatorMode } from "../operator/isOperatorMode";

type IngressRoute = "auto" | "hmi" | "direct";

const STORAGE_KEY = "ispf-ingress-route";

function readCachedRoute(): IngressRoute {
  if (!isOperatorMode()) {
    return "direct";
  }
  try {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    if (stored === "hmi" || stored === "direct") {
      return stored;
    }
  } catch {
    // private mode / blocked storage
  }
  return "auto";
}

function persistRoute(route: "hmi" | "direct") {
  try {
    sessionStorage.setItem(STORAGE_KEY, route);
  } catch {
    // ignore
  }
}

let cachedRoute: IngressRoute | null = null;

function currentRoute(): IngressRoute {
  if (cachedRoute === null) {
    cachedRoute = readCachedRoute();
  }
  return cachedRoute;
}

/** Clear cached ingress choice (e.g. on logout). */
export function resetIngressRouteCache(): void {
  cachedRoute = isOperatorMode() ? "auto" : "direct";
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

export function stripHmiIngressPrefix(path: string): string {
  if (path.startsWith(`${HMI_INGRESS_PREFIX}/`)) {
    return path.slice(HMI_INGRESS_PREFIX.length);
  }
  return path;
}

function isApiResponse(response: Response): boolean {
  const contentType = response.headers.get("content-type") ?? "";
  return contentType.includes("application/json") || contentType.includes("application/problem+json");
}

const PUBLIC_BOOTSTRAP_PATHS = new Set([
  "/api/v1/auth/login",
  "/api/v1/auth/config",
  "/api/v1/auth/me",
  "/api/v1/info",
]);

function isPublicBootstrapPath(path: string): boolean {
  const queryIndex = path.indexOf("?");
  const pathname = queryIndex >= 0 ? path.slice(0, queryIndex) : path;
  return PUBLIC_BOOTSTRAP_PATHS.has(pathname);
}

async function hasHmiSpringErrorPath(response: Response): Promise<boolean> {
  try {
    const body: unknown = await response.clone().json();
    if (body !== null && typeof body === "object" && "path" in body) {
      const errorPath = (body as { path?: unknown }).path;
      return typeof errorPath === "string" && errorPath.startsWith(`${HMI_INGRESS_PREFIX}/`);
    }
  } catch {
    // non-JSON or empty body
  }
  return false;
}

export interface IngressFallbackContext {
  route?: IngressRoute;
  directPath?: string;
}

/**
 * True when /hmi ingress is missing: SPA HTML, gateway errors, non-API 503,
 * or a JSON 403/404 caused by hitting Spring directly without an /hmi proxy
 * (either a Spring error body whose `path` starts with `/hmi/`, or an unconfirmed
 * `auto` route probing a public bootstrap endpoint).
 * Domain API JSON 403/404 responses on a confirmed `/hmi` route stay on `/hmi`.
 */
export async function shouldFallbackFromHmiIngress(
  response: Response,
  context?: IngressFallbackContext,
): Promise<boolean> {
  if (isApiResponse(response)) {
    if (response.status !== 403 && response.status !== 404) {
      return false;
    }
    if (context?.route === "auto" && context.directPath && isPublicBootstrapPath(context.directPath)) {
      return true;
    }
    return hasHmiSpringErrorPath(response);
  }
  if ([404, 502, 503, 504].includes(response.status)) {
    return true;
  }
  if (response.ok && (response.headers.get("content-type") ?? "").includes("text/html")) {
    return true;
  }
  return false;
}

/** Resolved path for fetch/WS after applying ingress prefix and any cached direct fallback. */
export function resolveIngressFetchPath(url: string): string {
  if (!url.startsWith("/")) {
    return url;
  }
  const hmiPath = resolveIngressPath(url);
  if (!hmiPath.startsWith(`${HMI_INGRESS_PREFIX}/`) || currentRoute() === "direct") {
    return stripHmiIngressPrefix(hmiPath);
  }
  return hmiPath;
}

/** Ordered WebSocket paths to try in operator auto mode (hmi-read first, then direct /ws). */
export function resolveIngressWebSocketPaths(url: string): string[] {
  if (!url.startsWith("/")) {
    return [url];
  }
  const hmiPath = resolveIngressPath(url);
  const directPath = stripHmiIngressPrefix(hmiPath);
  if (hmiPath === directPath) {
    return [directPath];
  }
  if (currentRoute() === "direct") {
    return [directPath];
  }
  // Always offer direct /ws as second choice: /hmi/ws often serves SPA HTML on single-node.
  return [hmiPath, directPath];
}

function markIngressRoute(route: "hmi" | "direct") {
  cachedRoute = route;
  persistRoute(route);
}

/** Force direct /api ingress for the rest of the session (e.g. WebSocket fallback). */
export function preferDirectIngressRoute(): void {
  markIngressRoute("direct");
}

/**
 * Operator mode: prefer /hmi/api (hmi-read). If ingress is absent (single node, Caddy, etc.),
 * fall back to /api on the same origin. Choice is cached for the browser session.
 */
export async function fetchWithIngressFallback(url: string, init?: RequestInit): Promise<Response> {
  if (!url.startsWith("/")) {
    return fetch(url, init);
  }

  const hmiPath = resolveIngressPath(url);
  const directPath = stripHmiIngressPrefix(hmiPath);

  const route = currentRoute();
  if (hmiPath === directPath || route === "direct") {
    return fetch(directPath, init);
  }

  // Even when "hmi" was cached, re-validate: pilot/single-node often serves SPA HTML at /hmi/*.
  // Blind reuse of a stale "hmi" sessionStorage value makes API callers parse HTML and fall back
  // to offline variable caches (topology stays green despite live faults).
  try {
    const response = await fetch(hmiPath, init);
    if (!(await shouldFallbackFromHmiIngress(response, { route, directPath }))) {
      markIngressRoute("hmi");
      return response;
    }
    markIngressRoute("direct");
    return fetch(directPath, init);
  } catch {
    markIngressRoute("direct");
    return fetch(directPath, init);
  }
}
