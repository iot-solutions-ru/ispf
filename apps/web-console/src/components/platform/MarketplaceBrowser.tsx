import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Alert, Button, Input, Select, Space } from "antd";
import { useTranslation } from "react-i18next";
import {
  filterMarketplaceListingsByKind,
  groupMarketplaceListingsByKind,
  marketplaceListingIdentifier,
  resolveMarketplaceListingKind,
  type MarketplaceKindFilter,
  type MarketplaceListingKind,
} from "../../api/marketplaceListingKind";
import {
  activateMarketplaceListing,
  fetchMarketplaceCatalog,
  fetchMarketplaces,
  installMarketplaceListing,
  uninstallAnalyticsPack,
  type MarketplaceListing,
} from "../../api/solutions";
import { fetchPlatformLicense } from "../../api/platformLicense";
import BundleLicenseErrorAlert from "./BundleLicenseErrorAlert";
import VendorContactModal, { hasMarketplaceVendorContact } from "./VendorContactModal";

const KIND_FILTERS: MarketplaceKindFilter[] = [
  "all",
  "application",
  "analytics-pack",
  "symbol-pack",
  "driver",
  "plugin",
  "workflow-template",
  "report-template",
  "ai-provider",
  "binding-pack",
  "other",
];

type InstalledFilter = "all" | "installed" | "not-installed";

function filterMarketplaceListingsByInstalled(
  listings: MarketplaceListing[],
  installedFilter: InstalledFilter
): MarketplaceListing[] {
  if (installedFilter === "all") {
    return listings;
  }
  if (installedFilter === "installed") {
    return listings.filter((listing) => listing.installed);
  }
  return listings.filter((listing) => !listing.installed);
}

function MarketplaceInstallationIdHint() {
  const { t } = useTranslation("system");
  const licenseQuery = useQuery({
    queryKey: ["platform-license"],
    queryFn: fetchPlatformLicense,
    staleTime: 60_000,
  });

  if (licenseQuery.isLoading || !licenseQuery.data?.installationId) {
    return null;
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(licenseQuery.data.installationId);
    } catch {
      // ignore
    }
  };

  return (
    <div className="marketplace-installation-id-hint">
      <span className="marketplace-installation-id-label">
        {t("solutions.marketplace.installationIdHint")}
      </span>
      <code className="mono marketplace-installation-id-value">{licenseQuery.data.installationId}</code>
      <Button type="link" className="marketplace-installation-id-copy" onClick={() => void copy()}>
        {t("solutions.marketplace.installationIdCopy")}
      </Button>
    </div>
  );
}

function MarketplaceKindBadge({ kind }: { kind: MarketplaceListingKind }) {
  const { t } = useTranslation("system");
  return (
    <span className={`marketplace-kind-badge marketplace-kind-badge--${kind}`}>
      {t(`solutions.marketplace.kind.${kind}`)}
    </span>
  );
}

function MarketplaceListingCard({
  listing,
  marketplaceId,
  onInstalled,
}: {
  listing: MarketplaceListing;
  marketplaceId: string;
  onInstalled: (message?: string) => void;
}) {
  const { t } = useTranslation("system");
  const [activationCode, setActivationCode] = useState("");
  const [showActivate, setShowActivate] = useState(false);
  const [showContact, setShowContact] = useState(false);
  const canContactVendor = hasMarketplaceVendorContact(listing);

  const installMutation = useMutation({
    mutationFn: () => installMarketplaceListing(marketplaceId, listing.slug),
    onSuccess: (result) => {
      const functions = Array.isArray(result.functions) ? result.functions.join(", ") : "";
      const appId = typeof result.appId === "string" ? result.appId : listing.appId;
      const operatorReady = result.operatorReady !== false;
      let message = functions
        ? t("solutions.marketplace.installOkWithFunctions", { functions })
        : t("solutions.installOk");
      if (isApplication && appId && operatorReady) {
        message = `${message} ${t("solutions.marketplace.installOkOpenOperator", { appId })}`;
      } else if (isApplication && !operatorReady) {
        message = `${message} ${t("solutions.marketplace.installOkNoOperator")}`;
      }
      onInstalled(message);
    },
  });

  const uninstallMutation = useMutation({
    mutationFn: () => uninstallAnalyticsPack(listing.packId ?? listing.slug),
    onSuccess: () => onInstalled(t("solutions.uninstallOk")),
  });

  const activateMutation = useMutation({
    mutationFn: () => activateMarketplaceListing(marketplaceId, listing.slug, activationCode),
    onSuccess: () => {
      setActivationCode("");
      setShowActivate(false);
      onInstalled();
    },
  });

  const kind = resolveMarketplaceListingKind(listing);
  const identifier = marketplaceListingIdentifier(listing);
  const isApplication = kind === "application";
  const isAnalyticsPack = kind === "analytics-pack";
  const isPaid = listing.pricing === "paid";
  const busy = installMutation.isPending || activateMutation.isPending || uninstallMutation.isPending;

  return (
    <>
      <article
        className={`solution-catalog-card solution-catalog-card--marketplace marketplace-listing-card marketplace-listing-card--${kind}`}
      >
        <header className="solution-catalog-card-head">
          <div className="marketplace-listing-card-title-block">
            <div className="marketplace-listing-card-title-row">
              <strong>{listing.title}</strong>
              <MarketplaceKindBadge kind={kind} />
            </div>
            <p className="op-muted solution-catalog-app-id">
              <code>{listing.slug}</code>
              {identifier ? (
                <>
                  {" "}
                  → <code>{identifier}</code>
                </>
              ) : null}
            </p>
          </div>
          <span className={`solution-catalog-version-pill ${isPaid ? "paid" : "free"}`}>
            {isPaid
              ? t("solutions.marketplace.paid", { price: listing.priceCents ? listing.priceCents / 100 : "—" })
              : t("solutions.marketplace.free")}
          </span>
        </header>
        <p>{listing.description}</p>
        <p className="op-muted solution-catalog-meta">
          {listing.vendorName && <span>{listing.vendorName}</span>}
          {listing.latestVersion && <span> · v{listing.latestVersion}</span>}
          {listing.minIspfVersion && <span> · ISPF ≥ {listing.minIspfVersion}</span>}
          {listing.installed && listing.activeVersion && (
            <span> · {t("solutions.marketplace.installedVersion", { version: listing.activeVersion })}</span>
          )}
          {listing.installed && !listing.activeVersion && (
            <span> · {t("solutions.marketplace.installed")}</span>
          )}
        </p>

        <footer className="solution-catalog-card-actions">
          {!isPaid && (
            <Button
              type="primary"
              size="small"
              disabled={busy}
              onClick={() => installMutation.mutate()}
            >
              {installMutation.isPending
                ? t("solutions.marketplace.installing")
                : listing.installed
                  ? t("solutions.reinstall")
                  : t("solutions.marketplace.installFree")}
            </Button>
          )}
          {isPaid && !showActivate && (
            <>
              <Button
                type="primary"
                size="small"
                onClick={() => setShowActivate(true)}
              >
                {t("solutions.marketplace.haveKey")}
              </Button>
              {canContactVendor && (
                <Button
                  size="small"
                  onClick={() => setShowContact(true)}
                >
                  {t("solutions.marketplace.contactVendor")}
                </Button>
              )}
            </>
          )}
          {isPaid && showActivate && (
            <div className="marketplace-activate-inline">
              <MarketplaceInstallationIdHint />
              <Input
                type="text"
                value={activationCode}
                placeholder={t("solutions.marketplace.activationPlaceholder")}
                onChange={(e) => setActivationCode(e.target.value)}
              />
              <Button
                type="primary"
                size="small"
                disabled={busy || !activationCode.trim()}
                onClick={() => activateMutation.mutate()}
              >
                {t("solutions.marketplace.activateInstall")}
              </Button>
              <Button size="small" onClick={() => setShowActivate(false)}>
                {t("solutions.marketplace.cancel")}
              </Button>
            </div>
          )}
          {!isPaid && canContactVendor && (
            <Button
              size="small"
              onClick={() => setShowContact(true)}
            >
              {t("solutions.marketplace.contactVendor")}
            </Button>
          )}
          {listing.installed && isAnalyticsPack && (
            <Button
              size="small"
              danger
              disabled={busy}
              onClick={() => uninstallMutation.mutate()}
            >
              {uninstallMutation.isPending ? t("solutions.uninstalling") : t("solutions.uninstall")}
            </Button>
          )}
          {listing.installed && isApplication && listing.appId && (
            <Button
              size="small"
              href={`/?mode=operator&app=${encodeURIComponent(listing.appId)}`}
            >
              {t("solutions.openOperator")}
            </Button>
          )}
        </footer>

        {installMutation.error && (
          <BundleLicenseErrorAlert error={installMutation.error} />
        )}
        {uninstallMutation.error && (
          <Alert type="error" showIcon message={String(uninstallMutation.error)} />
        )}
        {activateMutation.error && (
          <Space direction="vertical" size="small">
            <BundleLicenseErrorAlert error={activateMutation.error} />
            {canContactVendor && (
              <Button type="link" onClick={() => setShowContact(true)}>
                {t("solutions.marketplace.contactVendor")}
              </Button>
            )}
          </Space>
        )}
      </article>

      {showContact && (
        <VendorContactModal listing={listing} onClose={() => setShowContact(false)} />
      )}
    </>
  );
}

function MarketplaceListingGrid({
  listings,
  marketplaceId,
  onInstalled,
}: {
  listings: MarketplaceListing[];
  marketplaceId: string;
  onInstalled: (message?: string) => void;
}) {
  return (
    <div className="solution-catalog-grid">
      {listings.map((listing) => (
        <MarketplaceListingCard
          key={listing.slug}
          listing={listing}
          marketplaceId={marketplaceId}
          onInstalled={onInstalled}
        />
      ))}
    </div>
  );
}

export default function MarketplaceBrowser({ onInstalled }: { onInstalled: (message?: string) => void }) {
  const { t } = useTranslation("system");
  const [marketplaceId, setMarketplaceId] = useState("");
  const [query, setQuery] = useState("");
  const [pricing, setPricing] = useState("all");
  const [kindFilter, setKindFilter] = useState<MarketplaceKindFilter>("all");
  const [installedFilter, setInstalledFilter] = useState<InstalledFilter>("all");
  const [debouncedQuery, setDebouncedQuery] = useState("");

  const marketplacesQuery = useQuery({
    queryKey: ["marketplaces"],
    queryFn: fetchMarketplaces,
  });

  const endpoints = marketplacesQuery.data?.endpoints ?? [];
  const activeId = marketplaceId || marketplacesQuery.data?.defaultId || endpoints[0]?.id || "";

  const catalogQuery = useQuery({
    queryKey: ["marketplace-catalog", activeId, debouncedQuery, pricing],
    queryFn: () =>
      fetchMarketplaceCatalog(activeId, {
        q: debouncedQuery,
        pricing,
      }),
    enabled: Boolean(activeId) && marketplacesQuery.data?.enabled !== false,
  });

  const listings = catalogQuery.data?.listings ?? [];
  const visibleListings = useMemo(
    () => filterMarketplaceListingsByInstalled(listings, installedFilter),
    [listings, installedFilter]
  );
  const filteredListings = useMemo(
    () => filterMarketplaceListingsByKind(visibleListings, kindFilter),
    [visibleListings, kindFilter]
  );
  const groupedListings = useMemo(
    () => (kindFilter === "all" ? groupMarketplaceListingsByKind(visibleListings) : []),
    [visibleListings, kindFilter]
  );

  function applySearch() {
    setDebouncedQuery(query.trim());
  }

  if (marketplacesQuery.data?.enabled === false) {
    return <p className="op-muted">{t("solutions.marketplace.disabled")}</p>;
  }

  return (
    <section className="solution-catalog-section marketplace-browser">
      <div className="marketplace-toolbar">
        <label className="marketplace-field">
          <span>{t("solutions.marketplace.selectMarketplace")}</span>
          <Select
            value={activeId}
            onChange={setMarketplaceId}
            options={endpoints.map((ep) => ({ value: ep.id, label: ep.name }))}
          />
        </label>
        <label className="marketplace-field grow">
          <span>{t("solutions.marketplace.search")}</span>
          <Input
            type="search"
            value={query}
            placeholder={t("solutions.marketplace.searchPlaceholder")}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && applySearch()}
          />
        </label>
        <label className="marketplace-field">
          <span>{t("solutions.marketplace.pricingFilter")}</span>
          <Select
            value={pricing}
            onChange={setPricing}
            options={[
              { value: "all", label: t("solutions.marketplace.pricingAll") },
              { value: "free", label: t("solutions.marketplace.pricingFree") },
              { value: "paid", label: t("solutions.marketplace.pricingPaid") },
            ]}
          />
        </label>
        <label className="marketplace-field">
          <span>{t("solutions.marketplace.statusFilter")}</span>
          <Select
            value={installedFilter}
            onChange={(value) => setInstalledFilter(value)}
            options={[
              { value: "all", label: t("solutions.marketplace.statusAll") },
              { value: "installed", label: t("solutions.marketplace.statusInstalled") },
              { value: "not-installed", label: t("solutions.marketplace.statusNotInstalled") },
            ]}
          />
        </label>
        <Button onClick={applySearch}>
          {t("solutions.marketplace.searchBtn")}
        </Button>
      </div>

      {visibleListings.length > 0 && (
        <div className="marketplace-kind-filters" role="group" aria-label={t("solutions.marketplace.kindFilter")}>
          {KIND_FILTERS.map((filter) => {
            const count =
              filter === "all"
                ? visibleListings.length
                : filterMarketplaceListingsByKind(visibleListings, filter).length;
            if (filter !== "all" && count === 0) {
              return null;
            }
            const active = kindFilter === filter;
            return (
              <Button
                key={filter}
                className={`marketplace-kind-filter${active ? " active" : ""}${filter !== "all" ? ` marketplace-kind-filter--${filter}` : ""}`}
                type={active ? "primary" : "default"}
                aria-pressed={active}
                onClick={() => setKindFilter(filter)}
              >
                <span>{t(`solutions.marketplace.kindFilter.${filter}`)}</span>
                <span className="marketplace-kind-filter-count">{count}</span>
              </Button>
            );
          })}
        </div>
      )}

      {catalogQuery.error && (
        <Alert type="error" showIcon message={String(catalogQuery.error)} />
      )}
      {catalogQuery.isLoading && <p className="hint">{t("solutions.loading")}</p>}

      {!catalogQuery.isLoading && filteredListings.length === 0 && (
        <p className="op-muted">{t("solutions.marketplace.empty")}</p>
      )}

      {!catalogQuery.isLoading && kindFilter === "all" && groupedListings.length > 1 && (
        <div className="marketplace-kind-sections">
          {groupedListings.map((group) => (
            <section key={group.kind} className={`marketplace-kind-section marketplace-kind-section--${group.kind}`}>
              <header className="marketplace-kind-section-head">
                <h3>{t(`solutions.marketplace.kindSection.${group.kind}`)}</h3>
                <span className="marketplace-kind-section-count">{group.listings.length}</span>
              </header>
              <MarketplaceListingGrid
                listings={group.listings}
                marketplaceId={activeId}
                onInstalled={onInstalled}
              />
            </section>
          ))}
        </div>
      )}

      {!catalogQuery.isLoading && (kindFilter !== "all" || groupedListings.length <= 1) && filteredListings.length > 0 && (
        <MarketplaceListingGrid
          listings={filteredListings}
          marketplaceId={activeId}
          onInstalled={onInstalled}
        />
      )}
    </section>
  );
}
