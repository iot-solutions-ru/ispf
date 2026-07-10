import type { MarketplaceListing } from "./solutions";

/** Normalized marketplace artifact categories for UI filters and badges. */
export type MarketplaceListingKind =
  | "application"
  | "analytics-pack"
  | "symbol-pack"
  | "plugin"
  | "other";

export const MARKETPLACE_KIND_ORDER: MarketplaceListingKind[] = [
  "application",
  "analytics-pack",
  "symbol-pack",
  "plugin",
  "other",
];

export type MarketplaceKindFilter = "all" | MarketplaceListingKind;

function normalizeRawKind(value: string | null | undefined): string {
  return (value ?? "").trim().toLowerCase().replace(/_/g, "-");
}

export function resolveMarketplaceListingKind(listing: MarketplaceListing): MarketplaceListingKind {
  const raw = normalizeRawKind(listing.artifactKind) || normalizeRawKind(listing.kind);
  if (!raw || raw === "application-bundle" || raw === "application") {
    return "application";
  }
  if (raw === "analytics-pack") {
    return "analytics-pack";
  }
  if (raw === "symbol-pack") {
    return "symbol-pack";
  }
  if (raw.includes("plugin") || raw === "driver-pack" || raw === "driver") {
    return "plugin";
  }
  return "other";
}

export function marketplaceListingIdentifier(listing: MarketplaceListing): string | null {
  const kind = resolveMarketplaceListingKind(listing);
  if (kind === "analytics-pack") {
    return listing.packId?.trim() || null;
  }
  if (kind === "symbol-pack") {
    return listing.packId?.trim() || listing.slug?.trim() || null;
  }
  return listing.appId?.trim() || null;
}

export function filterMarketplaceListingsByKind(
  listings: MarketplaceListing[],
  kindFilter: MarketplaceKindFilter
): MarketplaceListing[] {
  if (kindFilter === "all") {
    return listings;
  }
  return listings.filter((listing) => resolveMarketplaceListingKind(listing) === kindFilter);
}

export function groupMarketplaceListingsByKind(
  listings: MarketplaceListing[]
): Array<{ kind: MarketplaceListingKind; listings: MarketplaceListing[] }> {
  const buckets = new Map<MarketplaceListingKind, MarketplaceListing[]>();
  for (const kind of MARKETPLACE_KIND_ORDER) {
    buckets.set(kind, []);
  }
  for (const listing of listings) {
    const kind = resolveMarketplaceListingKind(listing);
    buckets.get(kind)?.push(listing);
  }
  return MARKETPLACE_KIND_ORDER
    .map((kind) => ({ kind, listings: buckets.get(kind) ?? [] }))
    .filter((group) => group.listings.length > 0);
}

export function countMarketplaceListingsByKind(
  listings: MarketplaceListing[]
): Record<MarketplaceKindFilter, number> {
  const counts: Record<MarketplaceKindFilter, number> = {
    all: listings.length,
    application: 0,
    "analytics-pack": 0,
    "symbol-pack": 0,
    plugin: 0,
    other: 0,
  };
  for (const listing of listings) {
    counts[resolveMarketplaceListingKind(listing)] += 1;
  }
  return counts;
}
