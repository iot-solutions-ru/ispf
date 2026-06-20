export const SECURITY_ROLES_ROOT = "root.platform.security.roles";
export const SECURITY_ROLE_PATH_PREFIX = `${SECURITY_ROLES_ROOT}.`;

export function isSecurityRolesRoot(path: string): boolean {
  return path === SECURITY_ROLES_ROOT;
}

export function isSecurityRolePath(path: string): boolean {
  return path.startsWith(SECURITY_ROLE_PATH_PREFIX);
}

export function roleNameFromSecurityRolePath(path: string): string {
  return path.slice(SECURITY_ROLE_PATH_PREFIX.length);
}
