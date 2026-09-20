package com.ispf.server.application.bundle;

import com.ispf.server.scada.symbol.DropInSymbolPackLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * F-06: exercise listing/install logic without {@code @SpringBootTest}.
 */
@ExtendWith(MockitoExtension.class)
class MarketplaceSymbolListingServiceTest {

    @Mock
    private MarketplaceSymbolPackLocalService localService;

    @Mock
    private DropInSymbolPackLoader symbolPackLoader;

    private MarketplaceSymbolListingService service;

    @BeforeEach
    void setUp() {
        service = new MarketplaceSymbolListingService(localService, symbolPackLoader);
    }

    @Test
    void listsBundledAndLocalCatalog() {
        Map<String, Object> localPack = new LinkedHashMap<>();
        localPack.put("slug", "hvac-equipment-v1");
        localPack.put("validationStatus", "OK");
        when(localService.listLocalPacks()).thenReturn(Map.of(
                "status", "OK",
                "packs", List.of(localPack)
        ));
        when(symbolPackLoader.listInstalledPacks()).thenReturn(List.of());

        Map<String, Object> response = service.listSymbolPacks();

        assertThat(response.get("status")).isEqualTo("OK");
        assertThat(response.get("source")).isEqualTo("local");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listings = (List<Map<String, Object>>) response.get("listings");
        assertThat(listings).isNotEmpty();
        assertThat(listings.getFirst().get("slug")).isEqualTo("ispf-pid-v1");
        assertThat(listings.getFirst().get("artifactKind")).isEqualTo("symbol-pack");
        assertThat(listings).anySatisfy(listing -> {
            assertThat(listing.get("slug")).isEqualTo("hvac-equipment-v1");
            assertThat(listing.get("artifactKind")).isEqualTo("symbol-pack");
        });
    }

    @Test
    void installsLocalHvacDemoToFilesystem() throws Exception {
        Map<String, Object> installedResult = new LinkedHashMap<>();
        installedResult.put("status", "OK");
        installedResult.put("packId", "hvac-equipment-v1");
        installedResult.put("source", "local-marketplace");
        installedResult.put("path", "/tmp/symbol-packs/hvac-equipment-v1");
        when(localService.installLocalPack("hvac-equipment-v1")).thenReturn(installedResult);

        Map<String, Object> installed = service.installSymbolPack("hvac-equipment-v1");
        assertThat(installed.get("status")).isEqualTo("OK");
        assertThat(installed.get("packId")).isEqualTo("hvac-equipment-v1");
        assertThat(installed.get("source")).isEqualTo("local-marketplace");
        assertThat(installed.get("path")).asString().contains("hvac-equipment-v1");
        assertThat(installed.get("installationId")).asString().startsWith("symbol-install-");
    }
}
