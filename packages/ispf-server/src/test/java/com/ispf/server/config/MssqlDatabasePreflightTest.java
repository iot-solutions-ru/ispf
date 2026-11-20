package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MssqlDatabasePreflightTest {

    @Test
    void acceptsRcsiAndCaseSensitiveCollation() {
        assertThatCode(() -> MssqlDatabasePreflight.assertReady(true, "Latin1_General_100_CS_AS"))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesSnapshotOff() {
        assertThatThrownBy(() -> MssqlDatabasePreflight.assertReady(false, "Latin1_General_100_CS_AS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ_COMMITTED_SNAPSHOT");
    }

    @Test
    void refusesCaseInsensitiveCollation() {
        assertThatThrownBy(() -> MssqlDatabasePreflight.assertReady(true, "SQL_Latin1_General_CP1_CI_AS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("_CS_");
    }
}
