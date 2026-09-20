package com.ispf.core.export;

import java.io.IOException;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Service-provider interface for writing historian samples as Apache Parquet.
 *
 * <p>The implementation lives in the optional {@code ispf-export-parquet} module so that
 * {@code ispf-server} does not carry parquet-mr, Avro and hadoop-common (tens of MB of
 * transitive dependencies and CVE surface) unless a deployment actually exports Parquet.
 * Discovered through {@link ServiceLoader}; when no provider is present the server keeps
 * running and reports Parquet export as unavailable (HTTP 501 / cold archive skipped).</p>
 */
public interface HistoryParquetExporter {

    /** One flattened historian sample; {@code null} values are written as Parquet nulls. */
    record Row(
            String objectPath,
            String variableName,
            String field,
            String timestamp,
            Double value,
            String text,
            String ingestedAt
    ) {
    }

    /** Serialises the rows as one Parquet file (magic bytes {@code PAR1} at both ends). */
    byte[] export(List<Row> rows) throws IOException;

    /** Provider id for logs / status endpoints, e.g. {@code "parquet-mr/avro"}. */
    default String providerId() {
        return getClass().getName();
    }
}
