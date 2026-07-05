export const FEDERATION_ROOT = "root.platform.federation";

export function isFederationRoot(path: string): boolean {
  return path === FEDERATION_ROOT;
}

export function isFederatedCatalogPath(path: string): boolean {
  return path.startsWith(`${FEDERATION_ROOT}.`);
}

/** Peer slug segment from a catalog mirror path, e.g. `site-a` from `root.platform.federation.site-a.devices`. */
export function peerSlugFromCatalogMirrorPath(path: string): string | null {
  if (!isFederatedCatalogPath(path)) {
    return null;
  }
  const rest = path.slice(FEDERATION_ROOT.length + 1);
  const dot = rest.indexOf(".");
  return dot >= 0 ? rest.slice(0, dot) : rest;
}

/** Map local catalog mirror folder to remote subtree path using peer prefix. */
export function catalogMirrorToRemoteSubtree(localPath: string, peerPathPrefix: string): string | null {
  const slug = peerSlugFromCatalogMirrorPath(localPath);
  if (!slug) {
    return null;
  }
  const mirrorRoot = `${FEDERATION_ROOT}.${slug}`;
  const suffix = localPath.startsWith(mirrorRoot) ? localPath.slice(mirrorRoot.length) : "";
  const prefix = (peerPathPrefix?.trim() || "root.platform").replace(/\.+$/, "");
  return prefix + suffix;
}

export function peerNameToSlug(peerName: string): string {
  return peerName
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^-|-$/g, "");
}

export function findPeerForCatalogMirrorPath(
  path: string,
  peers: { id: string; name: string }[]
): { id: string; name: string; pathPrefix?: string } | undefined {
  const slug = peerSlugFromCatalogMirrorPath(path);
  if (!slug) {
    return undefined;
  }
  return peers.find((peer) => peerNameToSlug(peer.name) === slug);
}
