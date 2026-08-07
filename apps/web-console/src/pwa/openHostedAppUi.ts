/**
 * ADR-0054: console Service Worker navigateFallback must not steal /apps/* documents.
 * Until a SW build with /apps in denylist controls the page, same-origin Open app UI
 * navigations can still receive web-console index.html. Unregister briefly so the
 * new tab hits the network (HostedUiPackFilter), then re-register for the console.
 */

const SW_SCRIPT = "sw.js";

export function isSameOriginAppsUrl(url: string, origin: string = window.location.origin): boolean {
  try {
    const parsed = new URL(url, origin);
    return parsed.origin === origin && parsed.pathname.startsWith("/apps/");
  } catch {
    return false;
  }
}

/**
 * Operator "Open app UI" allowlist: relative hosted pack (`/apps/<id>/…`) or http(s) bridge.
 * Rejects javascript:/data:/vbscript: and non-/apps relative paths.
 */
export function isSafeOperatorLaunchUrl(url: string, origin: string = window.location.origin): boolean {
  const trimmed = url.trim();
  if (!trimmed) {
    return false;
  }
  const lower = trimmed.toLowerCase();
  if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
    return false;
  }
  if (trimmed.startsWith("/")) {
    return trimmed.startsWith("/apps/") && !trimmed.includes("://");
  }
  try {
    const parsed = new URL(trimmed, origin);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

export async function releaseServiceWorkersForAppsNavigation(): Promise<void> {
  if (!("serviceWorker" in navigator)) {
    return;
  }
  const regs = await navigator.serviceWorker.getRegistrations();
  await Promise.all(regs.map((reg) => reg.unregister()));
}

export async function ensureConsoleServiceWorker(): Promise<void> {
  if (!("serviceWorker" in navigator)) {
    return;
  }
  try {
    await navigator.serviceWorker.register(`/${SW_SCRIPT}`, { scope: "/" });
  } catch {
    // Offline / blocked — console still works without SW.
  }
}

/**
 * Open hosted ui-pack (or external bridge) without requiring Ctrl+F5.
 * Same-origin /apps/* temporarily drops the console SW so NavigationRoute cannot
 * SPA-fallback the document; external URLs open normally.
 */
export async function openHostedAppUi(url: string): Promise<void> {
  if (!isSafeOperatorLaunchUrl(url)) {
    console.warn("Blocked unsafe operator launch URL", url);
    return;
  }
  const target = new URL(url, window.location.href).href;
  const needsSwRelease = isSameOriginAppsUrl(target);

  if (needsSwRelease) {
    await releaseServiceWorkersForAppsNavigation();
  }

  const opened = window.open(target, "_blank", "noopener,noreferrer");
  if (!opened) {
    // Popup blocked — fall back to same-tab navigation.
    window.location.assign(target);
    return;
  }

  if (needsSwRelease) {
    void ensureConsoleServiceWorker();
  }
}
