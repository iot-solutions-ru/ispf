import { getAuthHeaders } from "../auth/session";

export interface HaystackTagSearchMatch {
  entityKind: "equip" | "point";
  objectPath: string;
  variableName?: string;
  dis?: string;
  unit?: string;
  tags?: Record<string, boolean>;
  haystackRef?: string;
}

export interface HaystackTagSearchResult {
  formatVersion: number;
  rootPath: string;
  tags: string[];
  entityKind: string;
  count: number;
  matches: HaystackTagSearchMatch[];
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function searchHaystackTags(params: {
  tags: string[];
  rootPath?: string;
  entityKind?: "equip" | "point" | "all";
  limit?: number;
}): Promise<HaystackTagSearchResult> {
  const query = new URLSearchParams();
  for (const tag of params.tags) {
    query.append("tags", tag);
  }
  if (params.rootPath?.trim()) {
    query.set("rootPath", params.rootPath.trim());
  }
  if (params.entityKind) {
    query.set("entityKind", params.entityKind);
  }
  if (params.limit != null) {
    query.set("limit", String(params.limit));
  }
  return fetch(`/api/v1/platform/haystack/search?${query.toString()}`, {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<HaystackTagSearchResult>(response));
}

export interface HaystackFilterQueryResult {
  formatVersion: number;
  filter: string;
  rootPath: string;
  entityKind: string;
  offset: number;
  limit: number;
  count: number;
  totalVisible?: number;
  matches: HaystackTagSearchMatch[];
}

export function queryHaystackFilter(params: {
  filter: string;
  rootPath?: string;
  entityKind?: "equip" | "point" | "all";
  offset?: number;
  limit?: number;
}): Promise<HaystackFilterQueryResult> {
  const query = new URLSearchParams();
  query.set("filter", params.filter.trim());
  if (params.rootPath?.trim()) {
    query.set("rootPath", params.rootPath.trim());
  }
  if (params.entityKind) {
    query.set("entityKind", params.entityKind);
  }
  if (params.offset != null) {
    query.set("offset", String(params.offset));
  }
  if (params.limit != null) {
    query.set("limit", String(params.limit));
  }
  return fetch(`/api/v1/platform/haystack/query?${query.toString()}`, {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<HaystackFilterQueryResult>(response));
}
