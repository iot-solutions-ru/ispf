package com.ispf.server.history;

import com.ispf.core.export.HistoryParquetExporter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryParquetExportGatewayTest {

    private static final VariableHistoryService.VariableHistoryResponse RESPONSE =
            new VariableHistoryService.VariableHistoryResponse(
                    "/devices/pump-1",
                    "temperature",
                    "value",
                    List.of(new VariableHistoryService.VariableHistorySample(
                            Instant.parse("2026-01-01T00:00:00Z"), 12.5, null, Instant.parse("2026-01-01T00:00:01Z")
                    ))
            );

    @Test
    void discoversModuleThroughServiceLoaderOnTestClasspath() throws Exception {
        HistoryParquetExportGateway gateway = new HistoryParquetExportGateway();

        assertThat(gateway.isAvailable()).isTrue();
        assertThat(gateway.providerId()).isEqualTo("parquet-mr/avro");
        byte[] body = gateway.export(RESPONSE);
        assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("PAR1");
        assertThat(new String(body, body.length - 4, 4, StandardCharsets.US_ASCII)).isEqualTo("PAR1");
    }

    @Test
    void flattensResponseIntoRows() throws Exception {
        AtomicReference<List<HistoryParquetExporter.Row>> captured = new AtomicReference<>();
        HistoryParquetExportGateway gateway = new HistoryParquetExportGateway(Optional.of(rows -> {
            captured.set(rows);
            return new byte[0];
        }));

        gateway.export(RESPONSE);

        assertThat(captured.get()).containsExactly(new HistoryParquetExporter.Row(
                "/devices/pump-1", "temperature", "value",
                "2026-01-01T00:00:00Z", 12.5, null, "2026-01-01T00:00:01Z"));
    }

    @Test
    void unavailableWhenModuleMissing() {
        HistoryParquetExportGateway gateway = new HistoryParquetExportGateway(Optional.empty());

        assertThat(gateway.isAvailable()).isFalse();
        assertThat(gateway.providerId()).isNull();
        assertThatThrownBy(() -> gateway.export(RESPONSE))
                .isInstanceOf(HistoryParquetExportGateway.ParquetExportUnavailableException.class)
                .hasMessageContaining("ispf-export-parquet");
    }
}
