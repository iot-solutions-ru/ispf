export const SECURITY_USERS_ROOT = "root.platform.security.users";
export const SECURITY_USER_PATH_PREFIX = `${SECURITY_USERS_ROOT}.`;

export function isSecurityUsersRoot(path: string): boolean {
  return path === SECURITY_USERS_ROOT;
}

export function isSecurityUserPath(path: string): boolean {
  return path.startsWith(SECURITY_USER_PATH_PREFIX);
}

export function usernameFromSecurityUserPath(path: string): string {
  return path.slice(SECURITY_USER_PATH_PREFIX.length);
}
