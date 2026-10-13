package com.ispf.server.application.uipack;

import com.ispf.server.config.UiPackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostedUiPackCspTest {

    private static final String EXPECTED =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'";

    @TempDir
    Path tempDir;

    private HostedUiPackFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        UiPackProperties properties = new UiPackProperties();
        properties.setPacksDir(tempDir.resolve("installed").toString());
        DropInUiPackLoader loader = new DropInUiPackLoader(properties, new ObjectMapper());
        loader.ensurePacksRoot();
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("ui-pack.json"), """
                {"appId":"demo-app","entry":"index.html"}
                """);
        Files.writeString(source.resolve("index.html"), "<html><body>pack</body></html>");
        loader.installPackDirectory(source, "demo-app");
        filter = new HostedUiPackFilter(loader);
    }

    @Test
    void policyMatchesAdr0073() {
        assertEquals(EXPECTED, HostedUiPackFilter.CONTENT_SECURITY_POLICY);
        assertFalse(HostedUiPackFilter.CONTENT_SECURITY_POLICY.contains("unsafe-eval"));
        assertTrue(HostedUiPackFilter.CONTENT_SECURITY_POLICY.contains("script-src 'self'"));
    }

    @Test
    void installedPackResponseCarriesCspCoopCorp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/apps/demo-app/");
        request.setRequestURI("/apps/demo-app/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
        });
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("pack"));
        assertIsolation(response);
    }

    @Test
    void missingPackStillCarriesCsp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/apps/missing/");
        request.setRequestURI("/apps/missing/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
        });
        assertEquals(404, response.getStatus());
        assertIsolation(response);
    }

    @Test
    void apiRoutesAreNotPackResponses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/info");
        request.setRequestURI("/api/v1/info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
        });
        assertNull(response.getHeader("Content-Security-Policy"));
    }

    private static void assertIsolation(MockHttpServletResponse response) {
        assertEquals(EXPECTED, response.getHeader("Content-Security-Policy"));
        assertEquals("same-origin", response.getHeader("Cross-Origin-Opener-Policy"));
        assertEquals("same-origin", response.getHeader("Cross-Origin-Resource-Policy"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }
}
