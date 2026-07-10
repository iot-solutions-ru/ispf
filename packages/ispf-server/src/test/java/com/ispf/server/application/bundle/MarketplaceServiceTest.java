package com.ispf.server.application.bundle;

import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.config.MarketplaceProperties;
import com.ispf.server.license.CommercialBundleLicenseSigner;
import com.ispf.server.license.InstallationIdService;
import com.ispf.server.platform.analytics.pack.DropInAnalyticsPackLoader;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceServiceTest {

    private HttpServer httpServer;
    private String baseUrl;
    private MarketplaceService service;

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/catalog", exchange -> {
            byte[] body = """
                    {"listings":[
                      {"slug":"free-app","title":"Free App","description":"demo","pricing":"free","appId":"free-app","vendorName":"Acme","latestVersion":"1.0.0","minIspfVersion":"0.1.0"},
                      {"slug":"paid-app","title":"Paid App","description":"commercial","pricing":"paid","appId":"paid-app","vendorName":"Acme"},
                      {"slug":"old-platform","title":"Old","description":"too new","pricing":"free","appId":"old-platform","vendorName":"Acme","minIspfVersion":"99.0.0"}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        httpServer.createContext("/api/v1/catalog/free-app", exchange -> {
            byte[] body = """
                    {"slug":"free-app","appId":"free-app","pricing":"free","title":"Free App"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        httpServer.createContext("/api/v1/catalog/free-app/download", exchange -> {
            byte[] body = """
                    {"version":"1.0.0","displayName":"Free App","schemaName":"app_free_app","migrations":[],"functions":[]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        httpServer.createContext("/api/v1/catalog/old-platform", exchange -> {
            byte[] body = """
                    {"slug":"old-platform","appId":"old-platform","pricing":"free","title":"Old","minIspfVersion":"99.0.0"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        httpServer.start();
        int port = httpServer.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;

        MarketplaceProperties properties = new MarketplaceProperties();
        properties.setEnabled(true);
        MarketplaceProperties.Endpoint endpoint = new MarketplaceProperties.Endpoint();
        endpoint.setId("test");
        endpoint.setName("Test Marketplace");
        endpoint.setBaseUrl(baseUrl);
        endpoint.setContactUrl("https://vendor.example/contact");
        endpoint.setDefaultEndpoint(true);
        properties.setEndpoints(List.of(endpoint));

        ApplicationDataStore dataStore = mock(ApplicationDataStore.class);
        when(dataStore.findApp("free-app")).thenReturn(java.util.Optional.empty());

        CommercialLicenseProperties licenseProperties = new CommercialLicenseProperties();
        licenseProperties.setDataDir(System.getProperty("java.io.tmpdir"));

        service = new MarketplaceService(
                properties,
                dataStore,
                mock(ApplicationBundleSnapshotStore.class),
                mock(ApplicationBundleDeployService.class),
                new InstallationIdService(licenseProperties),
                mock(CommercialBundleLicenseSigner.class),
                Optional.empty(),
                new ObjectMapper(),
                mock(DropInAnalyticsPackLoader.class),
                java.net.http.HttpClient.newHttpClient()
        );
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    @Test
    void browseFiltersByQueryAndPricing() throws Exception {
        Map<String, Object> all = service.browseCatalog("test", null, "all");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listings = (List<Map<String, Object>>) all.get("listings");
        assertEquals(3, listings.size());

        Map<String, Object> freeOnly = service.browseCatalog("test", null, "free");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> freeListings = (List<Map<String, Object>>) freeOnly.get("listings");
        assertEquals(2, freeListings.size());
        assertEquals("free-app", freeListings.get(0).get("slug"));

        Map<String, Object> search = service.browseCatalog("test", "commercial", "all");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> searchListings = (List<Map<String, Object>>) search.get("listings");
        assertEquals(1, searchListings.size());
        assertEquals("paid-app", searchListings.get(0).get("slug"));
    }

    @Test
    void installRejectsListingBelowMinIspfVersion() {
        assertThrows(IllegalArgumentException.class, () -> service.installFreeListing("test", "old-platform"));
    }

    @Test
    void listMarketplacesIncludesContactUrl() {
        Map<String, Object> response = service.listMarketplaces();
        assertTrue((Boolean) response.get("enabled"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) response.get("endpoints");
        assertEquals(1, endpoints.size());
        assertEquals("https://vendor.example/contact", endpoints.get(0).get("contactUrl"));
    }
}
