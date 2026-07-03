package com.ispf.server.event;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.server.persistence.EventHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "ispf.event-journal.async-enabled=true",
        "ispf.event-journal.batch-size=5",
        "ispf.event-journal.flush-interval-ms=25",
        "ispf.event-journal.queue-capacity=100",
        "ispf.event-journal.recent-cache-size=50"
})
class EventJournalAsyncWriterIntegrationTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private EventService eventService;

    @Autowired
    private EventJournalAsyncWriter eventJournalAsyncWriter;

    @Autowired
    private EventHistoryRepository eventHistoryRepository;

    @Test
    void fireReturnsIdImmediatelyAndPersistsAsync() throws Exception {
        DataSchema schema = DataSchema.builder("thresholdPayload")
                .field("value", FieldType.DOUBLE)
                .field("unit", FieldType.STRING)
                .build();
        DataRecord payload = DataRecord.single(schema, Map.of("value", 88.0, "unit", "C"));

        ObjectEvent fired = eventService.fire(DEMO_DEVICE, "thresholdExceeded", payload);

        assertNotNull(fired.id());
        assertEquals("thresholdExceeded", fired.eventName());
        assertTrue(eventService.list(DEMO_DEVICE, 5).stream().anyMatch(event -> event.id().equals(fired.id())));

        eventJournalAsyncWriter.awaitQueueDrain(5, TimeUnit.SECONDS);

        assertTrue(eventHistoryRepository.findById(fired.id()).isPresent());
    }
}
