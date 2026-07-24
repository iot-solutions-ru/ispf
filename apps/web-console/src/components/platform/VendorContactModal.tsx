import { useTranslation } from "react-i18next";
import { Button, Descriptions, Modal, Typography } from "antd";
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
    <Modal
      title={t("solutions.marketplace.contactVendor")}
      open
      onCancel={onClose}
      destroyOnHidden
      className="marketplace-vendor-contact-modal"
      footer={
        <Button onClick={onClose}>
          {t("solutions.marketplace.close")}
        </Button>
      }
    >
      <Typography.Paragraph type="secondary" className="marketplace-vendor-contact-listing">
          {listing.title}
      </Typography.Paragraph>

      <Descriptions column={1} size="small" className="marketplace-vendor-contact-kv">
          {isIndividual ? (
            <Descriptions.Item label={t("solutions.marketplace.vendorType")}>
              {t("solutions.marketplace.vendorIndividual")}
            </Descriptions.Item>
          ) : companyName ? (
            <Descriptions.Item label={t("solutions.marketplace.vendorCompany")}>
              {companyName}
            </Descriptions.Item>
          ) : null}

          {showBrand && (
            <Descriptions.Item label={t("solutions.marketplace.vendorBrand")}>
              {listing.vendorName}
            </Descriptions.Item>
          )}

          {!isIndividual && listing.vendorInn && (
            <Descriptions.Item label={t("solutions.marketplace.vendorInn")}>
              {listing.vendorInn}
            </Descriptions.Item>
          )}

          {listing.vendorContactPerson && (
            <Descriptions.Item label={t("solutions.marketplace.vendorContactPerson")}>
              {listing.vendorContactPerson}
            </Descriptions.Item>
          )}

          {isIndividual && listing.vendorName && (
            <Descriptions.Item label={t("solutions.marketplace.vendorPersonName")}>
              {listing.vendorName}
            </Descriptions.Item>
          )}

          {listing.vendorContactEmail && (
            <Descriptions.Item label={t("solutions.marketplace.vendorEmail")}>
              <a href={`mailto:${listing.vendorContactEmail}`}>{listing.vendorContactEmail}</a>
            </Descriptions.Item>
          )}

          {listing.vendorContactPhone && (
            <Descriptions.Item label={t("solutions.marketplace.vendorPhone")}>
              <a href={`tel:${listing.vendorContactPhone.replace(/\s/g, "")}`}>
                {listing.vendorContactPhone}
              </a>
            </Descriptions.Item>
          )}
      </Descriptions>
    </Modal>
  );
}
