import { getAuthHeaders } from "../auth/session";

export interface SolutionCatalogVersion {
  version: string;
  deployedAt?: string;
  active?: boolean;
}

export interface SolutionCatalogInstalled {
  appId: string;
  displayName?: string;
  schemaName?: string;
  activeVersion?: string;
  deployedAt?: string;
  changelog?: string;
  screenCount?: number;
  bundleDisplayName?: string;
  versions?: SolutionCatalogVersion[];
}

export interface SolutionCatalogAnalyticsPack {
  packId: string;
  version?: string;
  functions?: string[];
  helpers?: string[];
  licenseType?: string;
  minPlatformVersion?: string;
}

export interface SolutionCatalogResponse {
  installed: SolutionCatalogInstalled[];
  installedAnalyticsPacks?: SolutionCatalogAnalyticsPack[];
}

export interface MarketplaceEndpoint {
  id: string;
  name: string;
  baseUrl: string;
  contactUrl?: string;
  default?: boolean;
}

export interface MarketplaceListingsResponse {
  marketplaceId: string;
  marketplaceName: string;
  contactUrl?: string;
  listings: MarketplaceListing[];
  total: number;
}

export interface MarketplaceListing {
  slug: string;
  title: string;
  description: string;
  kind?: string;
  artifactKind?: string | null;
  pricing: string;
  priceCents?: number | null;
  appId?: string | null;
  packId?: string | null;
  vendorSlug?: string | null;
  vendorName?: string | null;
  vendorLegalName?: string | null;
  vendorInn?: string | null;
  vendorSellerKind?: "company" | "individual" | null;
  vendorContactPerson?: string | null;
  vendorContactEmail?: string | null;
  vendorContactPhone?: string | null;
  vendorContactUrl?: string | null;
  marketplaceContactUrl?: string | null;
  latestVersion?: string | null;
  minIspfVersion?: string;
  installed?: boolean;
  activeVersion?: string;
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchSolutionCatalog(): Promise<SolutionCatalogResponse> {
  return fetch("/api/v1/solutions/catalog", {
    headers: getAuthHeaders(),
  }).then((r) => parseJson<SolutionCatalogResponse>(r));
}

export function fetchMarketplaces(): Promise<{ enabled: boolean; defaultId: string; endpoints: MarketplaceEndpoint[] }> {
  return fetch("/api/v1/solutions/marketplaces", {
    headers: getAuthHeaders(),
  }).then((r) => parseJson<{ enabled: boolean; defaultId: string; endpoints: MarketplaceEndpoint[] }>(r));
}

export function fetchMarketplaceCatalog(
  marketplaceId: string,
  params?: { q?: string; pricing?: string; kind?: string }
): Promise<MarketplaceListingsResponse> {
  const search = new URLSearchParams();
  if (params?.q) search.set("q", params.q);
  if (params?.pricing && params.pricing !== "all") search.set("pricing", params.pricing);
  if (params?.kind && params.kind !== "all") search.set("kind", params.kind);
  const qs = search.toString();
  return fetch(
    `/api/v1/solutions/marketplaces/${encodeURIComponent(marketplaceId)}/catalog${qs ? `?${qs}` : ""}`,
    { headers: getAuthHeaders() }
  ).then((r) => parseJson<MarketplaceListingsResponse>(r));
}

export function installMarketplaceListing(
  marketplaceId: string,
  slug: string
): Promise<Record<string, unknown>> {
  return fetch(
    `/api/v1/solutions/marketplaces/${encodeURIComponent(marketplaceId)}/listings/${encodeURIComponent(slug)}/install`,
    { method: "POST", headers: getAuthHeaders() }
  ).then((r) => parseJson<Record<string, unknown>>(r));
}

export function activateMarketplaceListing(
  marketplaceId: string,
  slug: string,
  activationCode: string
): Promise<Record<string, unknown>> {
  return fetch(
    `/api/v1/solutions/marketplaces/${encodeURIComponent(marketplaceId)}/listings/${encodeURIComponent(slug)}/activate`,
    {
      method: "POST",
      headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
      body: JSON.stringify({ activationCode }),
    }
  ).then((r) => parseJson<Record<string, unknown>>(r));
}

export function uninstallApplication(appId: string): Promise<Record<string, unknown>> {
  return fetch(`/api/v1/solutions/installed/applications/${encodeURIComponent(appId)}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then((r) => parseJson<Record<string, unknown>>(r));
}

export function uninstallAnalyticsPack(packId: string): Promise<Record<string, unknown>> {
  return fetch(`/api/v1/solutions/installed/analytics-packs/${encodeURIComponent(packId)}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then((r) => parseJson<Record<string, unknown>>(r));
}
