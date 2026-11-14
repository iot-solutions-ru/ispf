package com.ispf.server.application.data;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    void aRepeatedMessageIdIsDeliveredOnce() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inbox", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
            ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
            relay.ensureTables();
            String first = relay.enqueue("plant-discrete-mes", "erp", "WORK_ORDER:WO-1", "{\"status\":\"complete\"}");
            String second = relay.enqueue("plant-discrete-mes", "erp", "WORK_ORDER:WO-1", "{\"status\":\"complete\"}");
            assertEquals(first, second);
            Integer rows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM public.application_outbox WHERE message_id = ?",
                    Integer.class,
                    "WORK_ORDER:WO-1"
            );
            assertEquals(1, rows);
            String endpoints = "erp=http://127.0.0.1:" + server.getAddress().getPort() + "/inbox";
            HttpOutboxDelivery delivery = new HttpOutboxDelivery(HttpOutboxDelivery.parseEndpoints(endpoints));
            assertEquals(1, relay.relayOnce(java.util.Set.of("erp"), delivery));
            assertEquals("SENT", relay.status(first));
            assertEquals(0, relay.relayOnce(java.util.Set.of("erp"), delivery));
            assertEquals(1, hits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void concurrentRelayDeliversAPendingRowOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inbox", exchange -> {
            hits.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
            ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
            relay.ensureTables();
            String id = relay.enqueue("plant", "erp", "once-1", "{}");
            String endpoints = "erp=http://127.0.0.1:" + server.getAddress().getPort() + "/inbox";
            HttpOutboxDelivery delivery = new HttpOutboxDelivery(HttpOutboxDelivery.parseEndpoints(endpoints));
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch done = new CountDownLatch(2);
            AtomicInteger sent = new AtomicInteger();
            for (int i = 0; i < 2; i++) {
                pool.execute(() -> {
                    started.countDown();
                    try {
                        assertTrue(started.await(5, TimeUnit.SECONDS));
                        sent.addAndGet(relay.relayOnce(java.util.Set.of("erp"), delivery));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(1, hits.get());
            assertEquals(1, sent.get());
            assertEquals("SENT", relay.status(id));
        } finally {
            release.countDown();
            pool.shutdownNow();
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
