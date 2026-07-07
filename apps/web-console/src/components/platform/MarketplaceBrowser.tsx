import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  activateMarketplaceListing,
  fetchMarketplaceCatalog,
  fetchMarketplaces,
  installMarketplaceListing,
  type MarketplaceListing,
} from "../../api/solutions";
import { fetchPlatformLicense } from "../../api/platformLicense";
import BundleLicenseErrorAlert from "./BundleLicenseErrorAlert";
import VendorContactModal, { hasMarketplaceVendorContact } from "./VendorContactModal";

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
      <button type="button" className="btn-link marketplace-installation-id-copy" onClick={() => void copy()}>
        {t("solutions.marketplace.installationIdCopy")}
      </button>
    </div>
  );
}

function MarketplaceListingCard({
  listing,
  marketplaceId,
  onInstalled,
}: {
  listing: MarketplaceListing;
  marketplaceId: string;
  onInstalled: () => void;
}) {
  const { t } = useTranslation("system");
  const [activationCode, setActivationCode] = useState("");
  const [showActivate, setShowActivate] = useState(false);
  const [showContact, setShowContact] = useState(false);
  const canContactVendor = hasMarketplaceVendorContact(listing);

  const installMutation = useMutation({
    mutationFn: () => installMarketplaceListing(marketplaceId, listing.slug),
    onSuccess: onInstalled,
  });

  const activateMutation = useMutation({
    mutationFn: () => activateMarketplaceListing(marketplaceId, listing.slug, activationCode),
    onSuccess: () => {
      setActivationCode("");
      setShowActivate(false);
      onInstalled();
    },
  });

  const isPaid = listing.pricing === "paid";
  const busy = installMutation.isPending || activateMutation.isPending;

  return (
    <>
      <article className="solution-catalog-card solution-catalog-card--marketplace">
        <header className="solution-catalog-card-head">
          <div>
            <strong>{listing.title}</strong>
            <p className="op-muted solution-catalog-app-id">
              <code>{listing.slug}</code> → <code>{listing.appId}</code>
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
        </p>

        <footer className="solution-catalog-card-actions">
          {!isPaid && (
            <button
              type="button"
              className="btn primary small"
              disabled={busy}
              onClick={() => installMutation.mutate()}
            >
              {listing.installed ? t("solutions.reinstall") : t("solutions.marketplace.installFree")}
            </button>
          )}
          {isPaid && !showActivate && (
            <>
              <button
                type="button"
                className="btn primary small"
                onClick={() => setShowActivate(true)}
              >
                {t("solutions.marketplace.haveKey")}
              </button>
              {canContactVendor && (
                <button
                  type="button"
                  className="btn small"
                  onClick={() => setShowContact(true)}
                >
                  {t("solutions.marketplace.contactVendor")}
                </button>
              )}
            </>
          )}
          {isPaid && showActivate && (
            <div className="marketplace-activate-inline">
              <MarketplaceInstallationIdHint />
              <input
                type="text"
                value={activationCode}
                placeholder={t("solutions.marketplace.activationPlaceholder")}
                onChange={(e) => setActivationCode(e.target.value)}
              />
              <button
                type="button"
                className="btn primary small"
                disabled={busy || !activationCode.trim()}
                onClick={() => activateMutation.mutate()}
              >
                {t("solutions.marketplace.activateInstall")}
              </button>
              <button type="button" className="btn small" onClick={() => setShowActivate(false)}>
                {t("solutions.marketplace.cancel")}
              </button>
            </div>
          )}
          {!isPaid && canContactVendor && (
            <button
              type="button"
              className="btn small"
              onClick={() => setShowContact(true)}
            >
              {t("solutions.marketplace.contactVendor")}
            </button>
          )}
          {listing.installed && (
            <a
              className="btn small"
              href={`/?mode=operator&app=${encodeURIComponent(listing.appId)}`}
            >
              {t("solutions.openOperator")}
            </a>
          )}
        </footer>

        {installMutation.error && (
          <BundleLicenseErrorAlert error={installMutation.error} />
        )}
        {activateMutation.error && (
          <div className="op-alert op-alert-error compact">
            <BundleLicenseErrorAlert error={activateMutation.error} />
            {canContactVendor && (
              <button type="button" className="btn-link" onClick={() => setShowContact(true)}>
                {t("solutions.marketplace.contactVendor")}
              </button>
            )}
          </div>
        )}
      </article>

      {showContact && (
        <VendorContactModal listing={listing} onClose={() => setShowContact(false)} />
      )}
    </>
  );
}

export default function MarketplaceBrowser({ onInstalled }: { onInstalled: () => void }) {
  const { t } = useTranslation("system");
  const [marketplaceId, setMarketplaceId] = useState("");
  const [query, setQuery] = useState("");
  const [pricing, setPricing] = useState("all");
  const [debouncedQuery, setDebouncedQuery] = useState("");

  const marketplacesQuery = useQuery({
    queryKey: ["marketplaces"],
    queryFn: fetchMarketplaces,
  });

  const endpoints = marketplacesQuery.data?.endpoints ?? [];
  const activeId = marketplaceId || marketplacesQuery.data?.defaultId || endpoints[0]?.id || "";

  const catalogQuery = useQuery({
    queryKey: ["marketplace-catalog", activeId, debouncedQuery, pricing],
    queryFn: () => fetchMarketplaceCatalog(activeId, { q: debouncedQuery, pricing }),
    enabled: Boolean(activeId) && marketplacesQuery.data?.enabled !== false,
  });

  const listings = catalogQuery.data?.listings ?? [];

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
          <select
            value={activeId}
            onChange={(e) => setMarketplaceId(e.target.value)}
          >
            {endpoints.map((ep) => (
              <option key={ep.id} value={ep.id}>
                {ep.name}
              </option>
            ))}
          </select>
        </label>
        <label className="marketplace-field grow">
          <span>{t("solutions.marketplace.search")}</span>
          <input
            type="search"
            value={query}
            placeholder={t("solutions.marketplace.searchPlaceholder")}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && applySearch()}
          />
        </label>
        <label className="marketplace-field">
          <span>{t("solutions.marketplace.pricingFilter")}</span>
          <select value={pricing} onChange={(e) => setPricing(e.target.value)}>
            <option value="all">{t("solutions.marketplace.pricingAll")}</option>
            <option value="free">{t("solutions.marketplace.pricingFree")}</option>
            <option value="paid">{t("solutions.marketplace.pricingPaid")}</option>
          </select>
        </label>
        <button type="button" className="btn" onClick={applySearch}>
          {t("solutions.marketplace.searchBtn")}
        </button>
      </div>

      {catalogQuery.error && (
        <div className="op-alert op-alert-error">{String(catalogQuery.error)}</div>
      )}
      {catalogQuery.isLoading && <p className="hint">{t("solutions.loading")}</p>}

      {!catalogQuery.isLoading && listings.length === 0 && (
        <p className="op-muted">{t("solutions.marketplace.empty")}</p>
      )}

      <div className="solution-catalog-grid">
        {listings.map((listing) => (
          <MarketplaceListingCard
            key={listing.slug}
            listing={listing}
            marketplaceId={activeId}
            onInstalled={onInstalled}
          />
        ))}
      </div>
    </section>
  );
}
