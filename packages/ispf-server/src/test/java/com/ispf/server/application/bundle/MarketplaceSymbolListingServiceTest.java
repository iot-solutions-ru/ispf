package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MarketplaceSymbolListingServiceTest {

    @Autowired
    private MarketplaceSymbolListingService service;

    @Test
    void listsBundledAndLocalCatalog() {
        Map<String, Object> response = service.listSymbolPacks();

        assertThat(response.get("status")).isEqualTo("OK");
        assertThat(response.get("source")).isIn("local", "bundled");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listings = (List<Map<String, Object>>) response.get("listings");
        assertThat(listings).isNotEmpty();
        assertThat(listings.getFirst().get("slug")).isEqualTo("ispf-pid-v1");
        assertThat(listings.getFirst().get("artifactKind")).isEqualTo("symbol-pack");
    }

    @Test
    void installsLocalHvacDemoToFilesystem() {
        Map<String, Object> installed = service.installSymbolPack("hvac-equipment-v1");
        assertThat(installed.get("status")).isEqualTo("OK");
        assertThat(installed.get("packId")).isEqualTo("hvac-equipment-v1");
        assertThat(installed.get("source")).isEqualTo("local-marketplace");
        assertThat(installed.get("path")).asString().contains("hvac-equipment-v1");
    }
}
