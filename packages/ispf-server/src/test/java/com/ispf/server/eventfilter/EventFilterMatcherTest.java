package com.ispf.server.eventfilter;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.expression.ExpressionEngine;
import com.ispf.server.eventfilter.EventFilterObjectService.EventFilterDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventFilterMatcherTest {

    private EventFilterMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new EventFilterMatcher(new ExpressionEngine());
    }

    @Test
    void matchesNamePathSeverityAndExpression() {
        EventFilterDefinition filter = new EventFilterDefinition(
                "root.platform.event-filters.alarms",
                "alarms",
                "Alarms",
                "",
                "alarm*",
                "root.platform.devices.**",
                40,
                100,
                0,
                "payload.severity >= 50",
                true
        );
        ObjectEvent hit = ObjectEvent.of(
                "root.platform.devices.pump-1",
                "alarmActive",
                EventLevel.WARNING,
                DataRecord.single(
                        DataSchema.builder("payload").field("code", FieldType.STRING).build(),
                        Map.of("code", "HI")
                )
        );
        ObjectEvent missName = ObjectEvent.of(
                "root.platform.devices.pump-1",
                "infoPing",
                EventLevel.WARNING,
                DataRecord.empty(DataSchema.builder("payload").build())
        );
        ObjectEvent missPath = ObjectEvent.of(
                "root.platform.workflows.demo",
                "alarmActive",
                EventLevel.WARNING,
                DataRecord.empty(DataSchema.builder("payload").build())
        );

        assertThat(matcher.matches(filter, hit)).isTrue();
        assertThat(matcher.matches(filter, missName)).isFalse();
        assertThat(matcher.matches(filter, missPath)).isFalse();
    }

    @Test
    void timeWindowExcludesOldEvents() {
        EventFilterDefinition filter = new EventFilterDefinition(
                "p",
                "recent",
                "Recent",
                "",
                "*",
                "root.**",
                0,
                100,
                60_000,
                "",
                true
        );
        ObjectEvent old = new ObjectEvent(
                "id-1",
                "root.platform.devices.x",
                "tick",
                EventLevel.INFO,
                DataRecord.empty(DataSchema.builder("payload").build()),
                Instant.now().minusSeconds(3600)
        );
        ObjectEvent recent = ObjectEvent.of(
                "root.platform.devices.x",
                "tick",
                EventLevel.INFO,
                DataRecord.empty(DataSchema.builder("payload").build())
        );
        assertThat(matcher.matches(filter, old)).isFalse();
        assertThat(matcher.matches(filter, recent)).isTrue();
    }

    @Test
    void disabledFilterMatchesNothing() {
        EventFilterDefinition filter = new EventFilterDefinition(
                "p", "f", "F", "", "*", "root.**", 0, 100, 0, "", false
        );
        ObjectEvent event = ObjectEvent.of(
                "root.platform.devices.x",
                "tick",
                EventLevel.INFO,
                DataRecord.empty(DataSchema.builder("payload").build())
        );
        assertThat(matcher.matches(filter, event)).isFalse();
    }
}
