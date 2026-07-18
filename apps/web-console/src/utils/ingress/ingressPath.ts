import { isOperatorMode } from "../operator/isOperatorMode";

/** Nginx ingress prefix for hmi-read replica (ADR-0032). */
export const HMI_INGRESS_PREFIX = "/hmi";

/**
 * Route operator-mode REST/WS through dedicated hmi-read ingress when present.
 * Admin console keeps /api and /ws on edge-api pool only.
 * Use fetchWithIngressFallback (ingressFetch.ts) for runtime fallback to /api.
 */
export function resolveIngressPath(path: string): string {
  if (!path.startsWith("/")) {
    return path;
  }
  if (!isOperatorMode()) {
    return path;
  }
  if (path.startsWith(`${HMI_INGRESS_PREFIX}/`)) {
    return path;
  }
  if (path.startsWith("/api/") || path.startsWith("/ws/")) {
    return `${HMI_INGRESS_PREFIX}${path}`;
  }
  return path;
}
