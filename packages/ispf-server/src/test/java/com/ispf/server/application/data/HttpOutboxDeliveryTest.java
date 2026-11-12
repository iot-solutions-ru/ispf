package com.ispf.server.application.data;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpOutboxDeliveryTest {

    @Test
    void outageKeepsTheRowUntilTheReceiverAcceptsItOnce() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inbox", exchange -> {
            int attempt = hits.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            int status = attempt == 1 ? 503 : 204;
            exchange.sendResponseHeaders(status, status == 204 ? -1 : 0);
            if (status != 204) {
                exchange.getResponseBody().write("down".getBytes(StandardCharsets.UTF_8));
            }
            assertEquals("m-http", exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            assertTrue(new String(body, StandardCharsets.UTF_8).contains("qty"));
            exchange.close();
        });
        server.start();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
            ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
            relay.ensureTables();
            String id = relay.enqueue("plant", "sap", "m-http", "{\"qty\":1}");
            String endpoints = "sap=http://127.0.0.1:" + server.getAddress().getPort() + "/inbox";
            HttpOutboxDelivery delivery = new HttpOutboxDelivery(HttpOutboxDelivery.parseEndpoints(endpoints));

            assertEquals(0, relay.relayOnce(delivery));
            assertEquals("PENDING", relay.status(id));
            assertEquals(1, relay.relayOnce(delivery));
            assertEquals("SENT", relay.status(id));
            assertEquals(0, relay.relayOnce(delivery));
            assertEquals(2, hits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emptyEndpointsDoNotTouchTheRelay() {
        ApplicationOutboxPump pump = new ApplicationOutboxPump(new ApplicationMessageRelay(null), "");
        assertEquals(0, pump.relayHttp());
    }

    @Test
    void httpPumpLeavesOtherConnectorsPending() {
        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
        relay.ensureTables();
        String sap = relay.enqueue("plant", "sap", "m-sap", "{}");
        String kafka = relay.enqueue("plant", "kafka", "m-kafka", "{}");
        assertEquals(1, relay.relayOnce(java.util.Set.of("sap"), (app, connector, message, body) -> { }));
        assertEquals("SENT", relay.status(sap));
        assertEquals("PENDING", relay.status(kafka));
    }

    @Test
    void endpointMustBeHttp() {
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> HttpOutboxDelivery.parseEndpoints("sap=file:///tmp/out")
        );
        assertTrue(ex.getMessage().contains("http"));
    }
}
