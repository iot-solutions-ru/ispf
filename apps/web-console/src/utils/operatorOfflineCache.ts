import type { DashboardView } from "../types/dashboard";
import type { OperatorManifest } from "../types/operatorManifest";
import type { OperatorUi } from "../types/operatorUi";
import type { VariableDto } from "../types";

const PREFIX = "ispf:op-offline:";

/** BL-151: operator offline cache TTL (8 hours). */
export const OFFLINE_CACHE_MAX_AGE_MS = 8 * 60 * 60 * 1000;

export interface OfflineCacheEnvelope<T> {
  cachedAt: string;
  payload: T;
}

export interface OfflineScreenSnapshot {
  kind: "table" | "report";
  rows: Record<string, unknown>[];
  labels?: Record<string, string>;
  truncated?: boolean;
}

function isExpired(cachedAt: string): boolean {
  const ageMs = Date.now() - Date.parse(cachedAt);
  return !Number.isFinite(ageMs) || ageMs > OFFLINE_CACHE_MAX_AGE_MS;
}

function readEnvelope<T>(key: string): OfflineCacheEnvelope<T> | null {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) {
      return null;
    }
    const envelope = JSON.parse(raw) as OfflineCacheEnvelope<T>;
    if (isExpired(envelope.cachedAt)) {
      localStorage.removeItem(key);
      return null;
    }
    return envelope;
  } catch {
    return null;
  }
}

function writeEnvelope<T>(key: string, payload: T): void {
  try {
    const envelope: OfflineCacheEnvelope<T> = {
      cachedAt: new Date().toISOString(),
      payload,
    };
    localStorage.setItem(key, JSON.stringify(envelope));
  } catch {
    // quota / private mode
  }
}

export function cacheOperatorManifest(appId: string, manifest: OperatorManifest): void {
  writeEnvelope(`${PREFIX}manifest:${appId}`, manifest);
}

export function readCachedOperatorManifest(appId: string): OperatorManifest | null {
  return readEnvelope<OperatorManifest>(`${PREFIX}manifest:${appId}`)?.payload ?? null;
}

export function cacheOperatorUi(appId: string, ui: OperatorUi): void {
  writeEnvelope(`${PREFIX}ui:${appId}`, ui);
}

export function readCachedOperatorUi(appId: string): OperatorUi | null {
  return readEnvelope<OperatorUi>(`${PREFIX}ui:${appId}`)?.payload ?? null;
}

export function cacheDashboardView(path: string, view: DashboardView): void {
  writeEnvelope(`${PREFIX}dashboard:${path}`, view);
}

export function readCachedDashboardView(path: string): DashboardView | null {
  return readEnvelope<DashboardView>(`${PREFIX}dashboard:${path}`)?.payload ?? null;
}

export function cacheVariables(objectPath: string, variables: VariableDto[]): void {
  writeEnvelope(`${PREFIX}vars:${objectPath}`, variables);
}

export function readCachedVariables(objectPath: string): VariableDto[] | null {
  return readEnvelope<VariableDto[]>(`${PREFIX}vars:${objectPath}`)?.payload ?? null;
}

export function cacheManifestScreenSnapshot(
  appId: string,
  screenId: string,
  snapshot: OfflineScreenSnapshot
): void {
  writeEnvelope(`${PREFIX}screen:${appId}:${screenId}`, snapshot);
}

export function readCachedManifestScreenSnapshot(
  appId: string,
  screenId: string
): OfflineScreenSnapshot | null {
  return readEnvelope<OfflineScreenSnapshot>(`${PREFIX}screen:${appId}:${screenId}`)?.payload ?? null;
}

export function cachedAtForManifest(appId: string): string | null {
  return readEnvelope<OperatorManifest>(`${PREFIX}manifest:${appId}`)?.cachedAt ?? null;
}

export function cachedAtForOperatorUi(appId: string): string | null {
  return readEnvelope<OperatorUi>(`${PREFIX}ui:${appId}`)?.cachedAt ?? null;
}

export function cachedAtForDashboard(path: string): string | null {
  return readEnvelope<DashboardView>(`${PREFIX}dashboard:${path}`)?.cachedAt ?? null;
}

/** Screens that participate in offline cache unless explicitly opted out. */
export function screenSupportsOfflineCache(screen: {
  offlineCache?: boolean;
  dashboard?: unknown;
  chart?: unknown;
  map?: unknown;
  report?: unknown;
  table?: unknown;
}): boolean {
  if (screen.offlineCache === false) {
    return false;
  }
  return Boolean(screen.dashboard ?? screen.chart ?? screen.map ?? screen.report ?? screen.table);
}
