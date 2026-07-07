import { useTranslation } from "react-i18next";
import type { MarketplaceListing } from "../../api/solutions";

interface VendorContactModalProps {
  listing: MarketplaceListing;
  onClose: () => void;
}

export function hasMarketplaceVendorContact(listing: MarketplaceListing): boolean {
  return Boolean(
    listing.vendorName
    || listing.vendorLegalName
    || listing.vendorInn
    || listing.vendorContactPerson
    || listing.vendorContactEmail
    || listing.vendorContactPhone
  );
}

function companyDisplayName(listing: MarketplaceListing): string | null {
  if (listing.vendorLegalName?.trim()) {
    return listing.vendorLegalName.trim();
  }
  if (listing.vendorName?.trim()) {
    return listing.vendorName.trim();
  }
  return null;
}

export default function VendorContactModal({ listing, onClose }: VendorContactModalProps) {
  const { t } = useTranslation("system");
  const isIndividual = listing.vendorSellerKind === "individual";
  const companyName = companyDisplayName(listing);
  const showBrand = Boolean(
    !isIndividual
    && listing.vendorLegalName?.trim()
    && listing.vendorName?.trim()
    && listing.vendorLegalName.trim() !== listing.vendorName.trim()
  );

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal marketplace-vendor-contact-modal"
        onClick={(event) => event.stopPropagation()}
      >
        <header>
          <h3>{t("solutions.marketplace.contactVendor")}</h3>
          <button type="button" className="icon-btn" onClick={onClose} aria-label={t("solutions.marketplace.close")}>
            ✕
          </button>
        </header>

        <p className="op-muted marketplace-vendor-contact-listing">
          {listing.title}
        </p>

        <dl className="solution-catalog-kv marketplace-vendor-contact-kv">
          {isIndividual ? (
            <div>
              <dt>{t("solutions.marketplace.vendorType")}</dt>
              <dd>{t("solutions.marketplace.vendorIndividual")}</dd>
            </div>
          ) : companyName ? (
            <div>
              <dt>{t("solutions.marketplace.vendorCompany")}</dt>
              <dd>{companyName}</dd>
            </div>
          ) : null}

          {showBrand && (
            <div>
              <dt>{t("solutions.marketplace.vendorBrand")}</dt>
              <dd>{listing.vendorName}</dd>
            </div>
          )}

          {!isIndividual && listing.vendorInn && (
            <div>
              <dt>{t("solutions.marketplace.vendorInn")}</dt>
              <dd>{listing.vendorInn}</dd>
            </div>
          )}

          {listing.vendorContactPerson && (
            <div>
              <dt>{t("solutions.marketplace.vendorContactPerson")}</dt>
              <dd>{listing.vendorContactPerson}</dd>
            </div>
          )}

          {isIndividual && listing.vendorName && (
            <div>
              <dt>{t("solutions.marketplace.vendorPersonName")}</dt>
              <dd>{listing.vendorName}</dd>
            </div>
          )}

          {listing.vendorContactEmail && (
            <div>
              <dt>{t("solutions.marketplace.vendorEmail")}</dt>
              <dd>
                <a href={`mailto:${listing.vendorContactEmail}`}>{listing.vendorContactEmail}</a>
              </dd>
            </div>
          )}

          {listing.vendorContactPhone && (
            <div>
              <dt>{t("solutions.marketplace.vendorPhone")}</dt>
              <dd>
                <a href={`tel:${listing.vendorContactPhone.replace(/\s/g, "")}`}>
                  {listing.vendorContactPhone}
                </a>
              </dd>
            </div>
          )}
        </dl>

        <footer className="modal-actions">
          <button type="button" className="btn" onClick={onClose}>
            {t("solutions.marketplace.close")}
          </button>
        </footer>
      </div>
    </div>
  );
}
