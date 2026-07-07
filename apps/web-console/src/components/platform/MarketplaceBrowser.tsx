import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  activateMarketplaceListing,
  fetchMarketplaceCatalog,
  fetchMarketplaces,
  installMarketplaceListing,
  type MarketplaceListing,
} from "../../api/solutions";

function vendorContactUrl(listing: MarketplaceListing, marketplaceContact?: string): string | null {
  return (
    listing.vendorContactUrl
    ?? (listing.vendorContactEmail ? `mailto:${listing.vendorContactEmail}` : null)
    ?? marketplaceContact
    ?? null
  );
}

function MarketplaceListingCard({
  listing,
  marketplaceId,
  marketplaceContact,
  onInstalled,
}: {
  listing: MarketplaceListing;
  marketplaceId: string;
  marketplaceContact?: string;
  onInstalled: () => void;
}) {
  const { t } = useTranslation("system");
  const [activationCode, setActivationCode] = useState("");
  const [showActivate, setShowActivate] = useState(false);
  const contactUrl = vendorContactUrl(listing, marketplaceContact);

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
            {contactUrl && (
              <a className="btn small" href={contactUrl} target="_blank" rel="noreferrer">
                {t("solutions.marketplace.contactVendor")}
              </a>
            )}
          </>
        )}
        {isPaid && showActivate && (
          <div className="marketplace-activate-inline">
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
        <p className="op-alert op-alert-error compact">{String(installMutation.error)}</p>
      )}
      {activateMutation.error && (
        <div className="op-alert op-alert-error compact">
          <p>{String(activateMutation.error)}</p>
          {contactUrl && (
            <a href={contactUrl} target="_blank" rel="noreferrer">
              {t("solutions.marketplace.contactVendor")}
            </a>
          )}
        </div>
      )}
    </article>
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
  const activeEndpoint = useMemo(
    () => endpoints.find((e) => e.id === activeId),
    [endpoints, activeId]
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
            marketplaceContact={catalogQuery.data?.contactUrl ?? activeEndpoint?.contactUrl}
            onInstalled={onInstalled}
          />
        ))}
      </div>
    </section>
  );
}
