package com.ispf.server.eventfilter;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.eventfilter.EventFilterObjectService.EventFilterDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void adHocExpressionFiltersByEventName() {
        ObjectEvent hit = ObjectEvent.of(
                "root.platform.devices.pump-1",
                "celHit",
                EventLevel.WARNING,
                DataRecord.empty(DataSchema.builder("payload").build())
        );
        ObjectEvent miss = ObjectEvent.of(
                "root.platform.devices.pump-1",
                "celMiss",
                EventLevel.WARNING,
                DataRecord.empty(DataSchema.builder("payload").build())
        );
        assertThat(matcher.matchesExpression("payload.eventName == \"celHit\"", hit)).isTrue();
        assertThat(matcher.matchesExpression("payload.eventName == \"celHit\"", miss)).isFalse();
        assertThat(matcher.matchesExpression("  ", hit)).isTrue();
    }

    @Test
    void metadataFieldsPassValidationAndMatch() {
        ObjectEvent warning = ObjectEvent.of(
                "root.platform.devices.room-a",
                "doorOpen",
                EventLevel.WARNING,
                DataRecord.empty(DataSchema.builder("payload").build())
        );
        ObjectEvent info = ObjectEvent.of(
                "root.platform.devices.room-b",
                "tick",
                EventLevel.INFO,
                DataRecord.empty(DataSchema.builder("payload").build())
        );

        matcher.validateExpression("eventName == 'doorOpen'");
        matcher.validateExpression("severity >= 50");
        matcher.validateExpression("level == 'WARNING'");
        matcher.validateExpression("objectPath == 'root.platform.devices.room-a'");

        assertThat(matcher.matchesExpression("eventName == 'doorOpen'", warning)).isTrue();
        assertThat(matcher.matchesExpression("eventName == 'doorOpen'", info)).isFalse();
        assertThat(matcher.matchesExpression("severity >= 50", warning)).isTrue();
        assertThat(matcher.matchesExpression("severity >= 50", info)).isFalse();
        assertThat(matcher.matchesExpression("level == 'WARNING'", warning)).isTrue();
        assertThat(matcher.matchesExpression("level == 'WARNING'", info)).isFalse();
        assertThat(matcher.matchesExpression("objectPath == 'root.platform.devices.room-a'", warning)).isTrue();
        assertThat(matcher.matchesExpression("payload.eventName == 'doorOpen'", warning)).isTrue();
    }

    @Test
    void invalidExpressionFailsValidation() {
        assertThatThrownBy(() -> matcher.validateExpression("payload."))
                .isInstanceOf(ExpressionException.class);
    }

    @Test
    void nonBooleanExpressionFailsValidationWithoutEvents() {
        assertThatThrownBy(() -> matcher.validateExpression("1"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("must return a boolean");
        assertThatThrownBy(() -> matcher.validateExpression("null"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("must return a boolean");
        matcher.validateExpression("true");
        matcher.validateExpression("payload.severity >= 50");
    }

    @Test
    void uncomputableExpressionFailsInsteadOfActingAsFalse() {
        ObjectEvent event = ObjectEvent.of(
                "root.platform.devices.pump-1",
                "tick",
                EventLevel.INFO,
                DataRecord.empty(DataSchema.builder("payload").build())
        );

        assertThatThrownBy(() -> matcher.matchesExpression("payload.", event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Event filter expression failed")
                .hasMessageContaining("payload.");

        assertThat(matcher.matchesExpression("false", event)).isFalse();
    }

    @Test
    void nonBooleanExpressionIsRejected() {
        ObjectEvent event = ObjectEvent.of(
                "root.platform.devices.pump-1",
                "tick",
                EventLevel.INFO,
                DataRecord.empty(DataSchema.builder("payload").build())
        );

        assertThatThrownBy(() -> matcher.matchesExpression("1", event))
                .hasMessageContaining("must return a boolean");
        assertThatThrownBy(() -> matcher.matchesExpression("null", event))
                .hasMessageContaining("must return a boolean");
        assertThat(matcher.matchesExpression("true", event)).isTrue();
        assertThat(matcher.matchesExpression("false", event)).isFalse();
    }
}
