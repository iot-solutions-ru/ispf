/**
 * One-shot: after deploying a console build that denylists /apps in Workbox,
 * drop any pre-existing SW that still SPA-fallbacks /apps (dogfood stands).
 * Marks completion in localStorage so we only auto-reload once per browser profile.
 */

export const APPS_DENYLIST_SW_MIGRATION_KEY = "ispf.pwa.appsDenylistSw.v1";

export async function migrateAppsDenylistServiceWorker(): Promise<boolean> {
  if (typeof window === "undefined" || !("serviceWorker" in navigator)) {
    return false;
  }
  try {
    if (window.localStorage.getItem(APPS_DENYLIST_SW_MIGRATION_KEY) === "done") {
      return false;
    }
  } catch {
    return false;
  }

  const regs = await navigator.serviceWorker.getRegistrations();
  if (regs.length === 0) {
    try {
      window.localStorage.setItem(APPS_DENYLIST_SW_MIGRATION_KEY, "done");
    } catch {
      /* ignore quota */
    }
    return false;
  }

  await Promise.all(regs.map((reg) => reg.unregister()));
  try {
    window.localStorage.setItem(APPS_DENYLIST_SW_MIGRATION_KEY, "done");
  } catch {
    /* ignore */
  }
  window.location.reload();
  return true;
}
