package com.ispf.server.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariableHistoryParquetExporterTest {

    @Test
    void writesParquetMagicBytes() throws Exception {
        VariableHistoryService.VariableHistoryResponse response = new VariableHistoryService.VariableHistoryResponse(
                "/devices/pump-1",
                "temperature",
                "value",
                List.of(new VariableHistoryService.VariableHistorySample(
                        Instant.parse("2026-01-01T00:00:00Z"),
                        12.5,
                        null,
                        null
                ))
        );

        byte[] body = VariableHistoryParquetExporter.export(response);

        assertThat(body.length).isGreaterThan(8);
        assertThat(new String(body, 0, 4)).isEqualTo("PAR1");
        assertThat(new String(body, body.length - 4, 4)).isEqualTo("PAR1");
    }
}
