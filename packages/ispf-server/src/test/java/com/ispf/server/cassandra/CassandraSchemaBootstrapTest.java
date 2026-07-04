package com.ispf.server.cassandra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CassandraSchemaBootstrapTest {

    @Test
    void acceptsValidKeyspaceNames() {
        assertThat(CassandraSchemaBootstrap.requireValidKeyspace("ispf")).isEqualTo("ispf");
        assertThat(CassandraSchemaBootstrap.requireValidKeyspace("ispf_lab")).isEqualTo("ispf_lab");
    }

    @Test
    void rejectsInvalidKeyspaceNames() {
        assertThatThrownBy(() -> CassandraSchemaBootstrap.requireValidKeyspace(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CassandraSchemaBootstrap.requireValidKeyspace("bad-name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CassandraSchemaBootstrap.requireValidKeyspace("drop;"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
