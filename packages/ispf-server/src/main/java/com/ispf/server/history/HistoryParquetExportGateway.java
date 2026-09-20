package com.ispf.server.history;

import com.ispf.core.export.HistoryParquetExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Bridges historian responses to the optional {@code ispf-export-parquet} module.
 *
 * <p>The provider is looked up once through {@link ServiceLoader}. When the module is not on
 * the classpath ({@code -Pispf.exportParquet=false} builds) {@link #isAvailable()} is
 * {@code false} and {@link #export} throws {@link ParquetExportUnavailableException}, which the
 * REST layer maps to HTTP 501 and the cold archive turns into a "skipped" run result.</p>
 */
@Component
public class HistoryParquetExportGateway {

    private static final Logger log = LoggerFactory.getLogger(HistoryParquetExportGateway.class);

    private final Optional<HistoryParquetExporter> exporter;

    public HistoryParquetExportGateway() {
        this(discover());
    }

    HistoryParquetExportGateway(Optional<HistoryParquetExporter> exporter) {
        this.exporter = exporter;
        if (exporter.isPresent()) {
            log.info("Parquet history export available via {}", exporter.get().providerId());
        } else {
            log.info("Parquet history export unavailable: ispf-export-parquet is not on the classpath");
        }
    }

    private static Optional<HistoryParquetExporter> discover() {
        List<HistoryParquetExporter> providers = ServiceLoader.load(
                HistoryParquetExporter.class, HistoryParquetExportGateway.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        if (providers.size() > 1) {
            log.warn("Multiple HistoryParquetExporter providers found {}, using the first",
                    providers.stream().map(HistoryParquetExporter::providerId).toList());
        }
        return providers.stream().findFirst();
    }

    public boolean isAvailable() {
        return exporter.isPresent();
    }

    /** Provider id for status output, or {@code null} when unavailable. */
    public String providerId() {
        return exporter.map(HistoryParquetExporter::providerId).orElse(null);
    }

    public byte[] export(VariableHistoryService.VariableHistoryResponse response) throws IOException {
        HistoryParquetExporter provider = exporter.orElseThrow(ParquetExportUnavailableException::new);
        List<HistoryParquetExporter.Row> rows = response.samples().stream()
                .map(sample -> new HistoryParquetExporter.Row(
                        response.objectPath(),
                        response.variableName(),
                        response.field(),
                        sample.ts() != null ? sample.ts().toString() : null,
                        sample.value(),
                        sample.text(),
                        sample.ingestedAt() != null ? sample.ingestedAt().toString() : null
                ))
                .toList();
        return provider.export(rows);
    }

    /** Thrown when Parquet export is requested but the optional module is not deployed. */
    public static final class ParquetExportUnavailableException extends UnsupportedOperationException {
        public ParquetExportUnavailableException() {
            super("Parquet export is not available on this server: the optional ispf-export-parquet module "
                    + "is not deployed (built with -Pispf.exportParquet=false). Use format=csv or format=json.");
        }
    }
}
