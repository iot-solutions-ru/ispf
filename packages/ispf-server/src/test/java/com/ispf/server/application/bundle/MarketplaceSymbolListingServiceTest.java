package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketplaceSymbolListingServiceTest {

    private final MarketplaceSymbolListingService service = new MarketplaceSymbolListingService();

    @Test
    void listsReferencePidPackStub() {
        Map<String, Object> response = service.listSymbolPacks();

        assertEquals("OK", response.get("status"));
        assertEquals("stub", response.get("source"));
        assertEquals(1, response.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listings = (List<Map<String, Object>>) response.get("listings");
        Map<String, Object> listing = listings.getFirst();
        assertEquals("ispf-pid-v1", listing.get("slug"));
        assertEquals("symbol-pack", listing.get("artifactKind"));
        assertEquals("free", listing.get("pricing"));
        assertEquals("ispf-pid-v1", listing.get("packId"));
        assertEquals(218, listing.get("symbolCount"));
    }
}
