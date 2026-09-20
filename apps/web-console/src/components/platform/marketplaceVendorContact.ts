// Extracted from VendorContactModal.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import type { MarketplaceListing } from "../../api/solutions";

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
