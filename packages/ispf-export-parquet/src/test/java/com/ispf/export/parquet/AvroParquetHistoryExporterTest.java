package com.ispf.export.parquet;

import com.ispf.core.export.HistoryParquetExporter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class AvroParquetHistoryExporterTest {

    @Test
    void writesParquetMagicBytes() throws Exception {
        byte[] body = new AvroParquetHistoryExporter().export(List.of(
                new HistoryParquetExporter.Row("/devices/pump-1", "temperature", "value",
                        "2026-01-01T00:00:00Z", 12.5, null, null),
                new HistoryParquetExporter.Row("/devices/pump-1", "temperature", "value",
                        "2026-01-01T00:01:00Z", null, "n/a", "2026-01-01T00:01:01Z")
        ));

        assertThat(body.length).isGreaterThan(8);
        assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("PAR1");
        assertThat(new String(body, body.length - 4, 4, StandardCharsets.US_ASCII)).isEqualTo("PAR1");
    }

    @Test
    void emptyInputStillProducesValidFile() throws Exception {
        byte[] body = new AvroParquetHistoryExporter().export(List.of());
        assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("PAR1");
        assertThat(new String(body, body.length - 4, 4, StandardCharsets.US_ASCII)).isEqualTo("PAR1");
    }

    @Test
    void isDiscoverableThroughServiceLoader() {
        List<HistoryParquetExporter> providers = ServiceLoader.load(HistoryParquetExporter.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        assertThat(providers).hasSize(1);
        assertThat(providers.getFirst()).isInstanceOf(AvroParquetHistoryExporter.class);
        assertThat(providers.getFirst().providerId()).isEqualTo("parquet-mr/avro");
    }
}
