package com.ispf.server.history;

import com.ispf.server.config.HistorianColdArchiveProperties;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Exports historian samples leaving the warm tier into cold Parquet archives (BL-202).
 */
@Service
public class HistorianColdArchiveService {

    private static final Logger log = LoggerFactory.getLogger(HistorianColdArchiveService.class);

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);

    private final HistorianTierProperties tierProperties;
    private final HistorianColdArchiveProperties archiveProperties;
    private final ObjectVariableRepository variableRepository;
    private final VariableHistoryQueryStore queryStore;
    private final ColdArchiveSink coldArchiveSink;

    public HistorianColdArchiveService(
            HistorianTierProperties tierProperties,
            HistorianColdArchiveProperties archiveProperties,
            ObjectVariableRepository variableRepository,
            VariableHistoryQueryStore queryStore,
            ColdArchiveSink coldArchiveSink
    ) {
        this.tierProperties = tierProperties;
        this.archiveProperties = archiveProperties;
        this.variableRepository = variableRepository;
        this.queryStore = queryStore;
        this.coldArchiveSink = coldArchiveSink;
    }

    public boolean isEnabled() {
        return archiveProperties.enabled() && coldArchiveSink.isConfigured();
    }

    /**
     * Exports one UTC day of samples that have aged past the warm-tier retention boundary.
     */
    public ColdArchiveRunResult exportEligibleDay() {
        if (!isEnabled()) {
            return ColdArchiveRunResult.skipped("cold archive disabled or sink not configured");
        }
        Instant warmCutoff = warmCutoff();
        Instant exportDayEnd = warmCutoff.truncatedTo(ChronoUnit.DAYS);
        Instant exportDayStart = exportDayEnd.minus(1, ChronoUnit.DAYS);
        if (!exportDayEnd.isAfter(Instant.EPOCH.plus(1, ChronoUnit.DAYS))) {
            return ColdArchiveRunResult.skipped("export day before epoch");
        }

        String dayKey = DAY_KEY.format(exportDayStart);
        List<ObjectVariableEntity> series = variableRepository.findByHistoryEnabledTrue();
        int seriesLimit = Math.max(archiveProperties.maxSeriesPerRun(), 1);
        int sampleLimit = Math.max(archiveProperties.maxSamplesPerSeries(), 1);

        int exportedSeries = 0;
        int exportedSamples = 0;
        int skippedEmpty = 0;

        for (ObjectVariableEntity entity : series) {
            if (exportedSeries >= seriesLimit) {
                break;
            }
            String fieldName = "value";
            List<VariableHistoryService.VariableHistorySample> samples = queryStore.query(
                    entity.getObjectPath(),
                    entity.getName(),
                    fieldName,
                    exportDayStart,
                    exportDayEnd.minus(1, ChronoUnit.MILLIS),
                    sampleLimit
            );
            if (samples.isEmpty()) {
                skippedEmpty++;
                continue;
            }
            try {
                byte[] parquet = toParquet(entity, fieldName, samples);
                String objectKey = objectKey(dayKey, entity, fieldName);
                coldArchiveSink.put(objectKey, parquet);
                exportedSeries++;
                exportedSamples += samples.size();
            } catch (IOException ex) {
                log.warn(
                        "Cold archive export failed for {}:{} — {}",
                        entity.getObjectPath(),
                        entity.getName(),
                        ex.getMessage()
                );
            }
        }

        log.info(
                "Cold archive export day {} — series={}, samples={}, skippedEmpty={}",
                dayKey,
                exportedSeries,
                exportedSamples,
                skippedEmpty
        );
        return new ColdArchiveRunResult(
                true,
                dayKey,
                exportedSeries,
                exportedSamples,
                skippedEmpty,
                null
        );
    }

    Instant warmCutoff() {
        int warmRetentionDays = Math.max(tierProperties.warmTier().getRetentionDays(), 1);
        return Instant.now().minus(warmRetentionDays, ChronoUnit.DAYS);
    }

    private static byte[] toParquet(
            ObjectVariableEntity entity,
            String fieldName,
            List<VariableHistoryService.VariableHistorySample> samples
    ) throws IOException {
        VariableHistoryService.VariableHistoryResponse response = new VariableHistoryService.VariableHistoryResponse(
                entity.getObjectPath(),
                entity.getName(),
                fieldName,
                samples
        );
        return VariableHistoryParquetExporter.export(response);
    }

    private static String objectKey(String dayKey, ObjectVariableEntity entity, String fieldName) {
        String safePath = entity.getObjectPath().replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeVar = entity.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeField = fieldName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return dayKey + "/" + safePath + "/" + safeVar + "-" + safeField + ".parquet";
    }

    public record ColdArchiveRunResult(
            boolean ran,
            String dayKey,
            int exportedSeries,
            int exportedSamples,
            int skippedEmpty,
            String skipReason
    ) {
        static ColdArchiveRunResult skipped(String reason) {
            return new ColdArchiveRunResult(false, null, 0, 0, 0, reason);
        }
    }
}
