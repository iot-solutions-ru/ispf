import { getAuthHeaders } from "../auth/session";

export interface ScadaInstalledSymbolPack {
  packId: string;
  version?: number | string;
  totalSymbols?: number;
  license?: string;
  path?: string;
}

export interface ScadaSymbolPackDetail extends ScadaInstalledSymbolPack {
  status?: string;
  categories?: Array<{
    id: string;
    file?: string;
    count?: number;
    symbols?: Array<{
      id: string;
      category: string;
      nameEn: string;
      nameRu?: string;
      defaultWidth: number;
      defaultHeight: number;
      viewBox: string;
      svg: string;
      ports?: Array<{ id: string; x: number; y: number }>;
      tags?: string[];
    }>;
  }>;
}

export function fetchInstalledSymbolPacks(): Promise<{
  status: string;
  count: number;
  packs: ScadaInstalledSymbolPack[];
}> {
  return fetch("/api/v1/scada/symbol-packs", {
    headers: getAuthHeaders(),
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(`Symbol packs list failed: ${response.status}`);
    }
    return response.json();
  });
}

export function fetchSymbolPackDetail(packId: string): Promise<ScadaSymbolPackDetail> {
  return fetch(`/api/v1/scada/symbol-packs/${encodeURIComponent(packId)}`, {
    headers: getAuthHeaders(),
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(`Symbol pack ${packId} failed: ${response.status}`);
    }
    return response.json();
  });
}
