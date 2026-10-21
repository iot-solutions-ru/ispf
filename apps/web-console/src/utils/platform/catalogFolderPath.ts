/**
 * A catalog folder lives at a fixed place in the tree:
 * `root.platform.{leaf}`, `root.tenant.{id}.platform.{leaf}`,
 * or directly under an application (`applications.{appId}.{leaf}`).
 * An object that is merely named `{leaf}` is not a folder.
 */
export function isCatalogFolderPath(path: string, leaf: string): boolean {
  if (!path || !leaf) {
    return false;
  }
  if (path === `root.platform.${leaf}`) {
    return true;
  }
  const parts = path.split(".");
  if (
    parts.length === 5
    && parts[0] === "root"
    && parts[1] === "tenant"
    && parts[3] === "platform"
    && parts[4] === leaf
    && parts[2].length > 0
  ) {
    return true;
  }
  if (
    parts.length === 5
    && parts[0] === "root"
    && parts[1] === "platform"
    && parts[2] === "applications"
    && parts[4] === leaf
    && parts[3].length > 0
  ) {
    return true;
  }
  return (
    parts.length === 7
    && parts[0] === "root"
    && parts[1] === "tenant"
    && parts[3] === "platform"
    && parts[4] === "applications"
    && parts[6] === leaf
    && parts[2].length > 0
    && parts[5].length > 0
  );
}
