package com.ispf.server.notification;

import com.ispf.server.config.NotificationProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationDispatchServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsJsonToAllowedWebhook() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            latch.countDown();
        });
        server.start();

        NotificationDispatchService service = new NotificationDispatchService(
                new NotificationProperties(),
                new ObjectMapper()
        );
        service.sendWebhook(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/hook",
                Map.of("eventName", "thresholdExceeded")
        );

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(body.get()).contains("thresholdExceeded");
    }

    @Test
    void rejectsCloudMetadataWebhookUrl() {
        NotificationDispatchService service = new NotificationDispatchService(
                new NotificationProperties(),
                new ObjectMapper()
        );
        assertThatThrownBy(() -> service.sendWebhook("http://169.254.169.254/latest/meta-data", Map.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsNonHttpWebhookUrl() {
        NotificationDispatchService service = new NotificationDispatchService(
                new NotificationProperties(),
                new ObjectMapper()
        );
        assertThatThrownBy(() -> service.sendWebhook("file:///etc/passwd", Map.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsBlankWebhookUrl() throws IOException {
        NotificationDispatchService service = new NotificationDispatchService(
                new NotificationProperties(),
                new ObjectMapper()
        );
        assertThatThrownBy(() -> service.sendWebhook("  ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
