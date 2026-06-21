export const FEDERATION_ROOT = "root.platform.federation";

export function isFederationRoot(path: string): boolean {
  return path === FEDERATION_ROOT;
}

export function isFederatedCatalogPath(path: string): boolean {
  return path.startsWith(`${FEDERATION_ROOT}.`);
}
