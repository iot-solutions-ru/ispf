export const TENANTS_ROOT = "root.tenant";

export function isTenantsRoot(path: string): boolean {
  return path === TENANTS_ROOT;
}
