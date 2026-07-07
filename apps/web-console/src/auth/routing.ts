import type { AuthSession } from "./session";
import { isConfiguratorSession } from "./session";

export function shouldOpenOperatorShell(
  session: AuthSession | null,
  appMode: "admin" | "operator"
): boolean {
  if (!session) {
    return false;
  }
  const params = new URLSearchParams(window.location.search);
  if (params.get("mode") === "admin" && isConfiguratorSession(session)) {
    return false;
  }
  if (appMode === "admin" && isConfiguratorSession(session)) {
    return false;
  }
  if (params.get("mode") === "operator" || appMode === "operator") {
    return true;
  }
  if (!isConfiguratorSession(session)) {
    return true;
  }
  return session.autoStartEnabled === true && Boolean(session.autoStartApp);
}

export function resolveOperatorAppId(
  session: AuthSession | null,
  params: URLSearchParams = new URLSearchParams(window.location.search)
): string | null {
  const fromUrl = params.get("app");
  if (fromUrl) {
    return fromUrl;
  }
  if (session?.autoStartEnabled && session.autoStartApp) {
    return session.autoStartApp;
  }
  return null;
}

export function resolveInitialAppMode(session: AuthSession | null): "admin" | "operator" {
  const params = new URLSearchParams(window.location.search);
  const urlMode = params.get("mode");
  if (urlMode === "operator") {
    return "operator";
  }
  if (urlMode === "admin") {
    return "admin";
  }
  if (!isConfiguratorSession(session) || (session?.autoStartEnabled && session.autoStartApp)) {
    return "operator";
  }
  return "admin";
}
