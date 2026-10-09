package com.ispf.server.correlator;

import com.ispf.server.automation.AutomationRuleIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class EventCorrelatorPayloadParseTest {

    private static final String OBJECT_PATH = "root.platform.devices.correlator-payload-probe";

    @Autowired
    private EventCorrelatorService correlatorService;

    @Autowired
    private AutomationRuleIndex ruleIndex;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void unreadablePayloadFailsInsteadOfAnEmptyMap() {
        EventCorrelator created = index("bad-payload", "probe-bad");
        insertJournal("probe-bad", "{not-json");

        assertThatThrownBy(() -> correlatorService.processEventFired(OBJECT_PATH, "probe-bad"))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).startsWith("Correlator payload parse failed: ");
                    assertThat(ex.getCause()).isNotNull();
                    assertThat(ex.getCause().getMessage()).isNotBlank();
                    assertThat(ex.getMessage()).contains(ex.getCause().getMessage());
                });
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();
    }

    @Test
    @Transactional
    void blankPayloadStaysAnEmptyMap() {
        EventCorrelator created = index("blank-payload", "probe-blank");
        insertJournal("probe-blank", "");

        correlatorService.processEventFired(OBJECT_PATH, "probe-blank");

        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNotNull();
    }

    private EventCorrelator index(String name, String eventName) {
        EventCorrelator created = correlatorService.create(new EventCorrelatorService.CreateCorrelatorRequest(
                name,
                OBJECT_PATH,
                CorrelatorPatternType.COUNT,
                eventName,
                null,
                60,
                1,
                0,
                0,
                CorrelatorActionType.SET_VARIABLE,
                "flag=1",
                "true",
                true
        ));
        ruleIndex.addCorrelator(created);
        return created;
    }

    private void insertJournal(String eventName, String payloadJson) {
        jdbcTemplate.update(
                """
                        INSERT INTO event_history (id, object_path, event_name, level, payload_json, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(),
                OBJECT_PATH,
                eventName,
                "INFO",
                payloadJson,
                Timestamp.from(Instant.parse("2099-01-01T00:00:00Z"))
        );
    }
}
