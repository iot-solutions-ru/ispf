export type IspfRole = "admin" | "operator";

const STORAGE_KEY = "ispf-role";

export function getStoredRole(): IspfRole {
  const value = localStorage.getItem(STORAGE_KEY);
  return value === "operator" ? "operator" : "admin";
}

export function setStoredRole(role: IspfRole): void {
  localStorage.setItem(STORAGE_KEY, role);
}

export function isAdminRole(role: IspfRole): boolean {
  return role === "admin";
}
