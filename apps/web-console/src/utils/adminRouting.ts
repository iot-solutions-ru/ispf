/** Admin explorer deep-link helpers (`?path=root.platform...`). */

export function readAdminPathFromUrl(search: string): string | null {
  const path = new URLSearchParams(search).get("path");
  if (!path || !path.trim()) {
    return null;
  }
  return path.trim();
}

export function resolveInitialAdminPath(search: string, storedPath: string): string {
  return readAdminPathFromUrl(search) ?? storedPath;
}

export function syncAdminPathToUrl(path: string | null): void {
  const url = new URL(window.location.href);
  if (path && path !== "root") {
    url.searchParams.set("path", path);
  } else {
    url.searchParams.delete("path");
  }
  window.history.replaceState({}, "", url.toString());
}

export function clearInvalidAdminPathFromUrl(): void {
  const url = new URL(window.location.href);
  if (!url.searchParams.has("path")) {
    return;
  }
  url.searchParams.delete("path");
  window.history.replaceState({}, "", url.toString());
}
