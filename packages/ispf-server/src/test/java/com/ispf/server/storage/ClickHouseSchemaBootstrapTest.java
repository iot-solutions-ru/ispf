package com.ispf.server.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseSchemaBootstrapTest {

    @Test
    void acceptsValidIdentifiers() {
        assertThat(ClickHouseSchemaBootstrap.requireValidIdentifier("ispf", "database")).isEqualTo("ispf");
        assertThat(ClickHouseSchemaBootstrap.qualifiedTable("ispf", "event_history"))
                .isEqualTo("ispf.event_history");
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertThatThrownBy(() -> ClickHouseSchemaBootstrap.requireValidIdentifier("", "database"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClickHouseSchemaBootstrap.requireValidIdentifier("bad-name", "table"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClickHouseSchemaBootstrap.requireValidIdentifier("drop;", "database"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
