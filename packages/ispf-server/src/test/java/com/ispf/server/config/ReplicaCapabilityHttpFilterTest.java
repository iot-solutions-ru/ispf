package com.ispf.server.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReplicaCapabilityHttpFilterTest {

    @Test
    void ioProfileAllowsLoopbackPlatformMetrics() throws Exception {
        ClusterProperties cluster = ioClusterProperties();
        ReplicaCapabilityHttpFilter filter = new ReplicaCapabilityHttpFilter(cluster);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/platform/metrics");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void ioProfileRejectsExternalPlatformMetrics() throws Exception {
        ClusterProperties cluster = ioClusterProperties();
        ReplicaCapabilityHttpFilter filter = new ReplicaCapabilityHttpFilter(cluster);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/platform/metrics");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertEquals("{\"error\":\"REPLICA_CAPABILITY_DENIED\",\"message\":\"Replica profile does not serve public HTTP API\"}",
                response.getContentAsString());
    }

    @Test
    void ioProfileStillRejectsExternalObjectApi() throws Exception {
        ClusterProperties cluster = ioClusterProperties();
        ReplicaCapabilityHttpFilter filter = new ReplicaCapabilityHttpFilter(cluster);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/objects");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
    }

    @Test
    void analyticsProfileAllowsMaterializerStatusFromNginx() throws Exception {
        ClusterProperties cluster = analyticsClusterProperties();
        ReplicaCapabilityHttpFilter filter = new ReplicaCapabilityHttpFilter(cluster);

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/platform/analytics/rollups/materializer/status"
        );
        request.setRemoteAddr("172.18.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    private static ClusterProperties analyticsClusterProperties() {
        return new ClusterProperties(
                true,
                true,
                30,
                10,
                15,
                10,
                30,
                true,
                2,
                8,
                4,
                6,
                500,
                true,
                true,
                500,
                true,
                "analytics",
                "",
                "all",
                true,
                2000,
                2,
                true,
                1,
                8,
                50,
                6,
                500,
                1800
        );
    }

    private static ClusterProperties ioClusterProperties() {
        return new ClusterProperties(
                true,
                true,
                30,
                10,
                15,
                10,
                30,
                true,
                2,
                8,
                4,
                6,
                500,
                true,
                true,
                500,
                true,
                "io",
                "",
                "all",
                true,
                2000,
                2,
                true,
                1,
                8,
                50,
                6,
                500,
                1800
        );
    }
}
