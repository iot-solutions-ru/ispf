package com.ispf.server.audit;

import com.ispf.server.config.AuditProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSiemWebhookDispatcherTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsAuditEventJsonToConfiguredWebhook() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siem", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            latch.countDown();
        });
        server.start();
        int port = server.getAddress().getPort();

        AuditProperties properties = new AuditProperties();
        properties.setSiemWebhookUrl("http://127.0.0.1:" + port + "/siem");
        properties.setSiemAsync(false);
        properties.setSiemTimeoutSeconds(3);

        AuditSiemWebhookDispatcher dispatcher = new AuditSiemWebhookDispatcher(
                properties,
                new ObjectMapper()
        );
        dispatcher.dispatch(new AuditEventService.AuditEvent(
                "evt-1",
                "auth",
                "login.success",
                "admin",
                "user",
                "admin",
                "{}",
                Instant.parse("2026-07-19T08:00:00Z")
        ));

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(body.get()).contains("login.success").contains("evt-1").contains("ispf-audit");
    }

    @Test
    void noOpWhenWebhookUrlBlank() throws IOException {
        AuditProperties properties = new AuditProperties();
        properties.setSiemAsync(false);
        AuditSiemWebhookDispatcher dispatcher = new AuditSiemWebhookDispatcher(
                properties,
                new ObjectMapper()
        );
        dispatcher.dispatch(new AuditEventService.AuditEvent(
                "evt-2", "mfa", "enrollment.started", "u", "user", "u", null, Instant.now()
        ));
        // no exception
    }
}
