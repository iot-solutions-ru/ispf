package com.ispf.server.history;

import com.ispf.server.config.HistorianColdArchiveProperties;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistorianColdArchiveServiceTest {

    @TempDir
    Path tempDir;

    private HistorianTierProperties tierProperties;
    private HistorianColdArchiveProperties archiveProperties;
    private ObjectVariableRepository variableRepository;
    private VariableHistoryQueryStore queryStore;
    private FilesystemColdArchiveSink sink;
    private HistorianColdArchiveService service;

    @BeforeEach
    void setUp() {
        tierProperties = new HistorianTierProperties();
        tierProperties.warmTier().setRetentionDays(90);
        tierProperties.coldTier().getCold().setBucket("test-bucket");
        tierProperties.coldTier().getCold().setPrefix("archive/");

        archiveProperties = new HistorianColdArchiveProperties(true, tempDir.toString(), 10, 100);
        variableRepository = mock(ObjectVariableRepository.class);
        queryStore = mock(VariableHistoryQueryStore.class);
        sink = new FilesystemColdArchiveSink(tierProperties, archiveProperties);
        service = new HistorianColdArchiveService(
                tierProperties,
                archiveProperties,
                variableRepository,
                queryStore,
                sink
        );
    }

    @Test
    void exportsParquetUnderConfiguredPrefix() throws Exception {
        ObjectVariableEntity entity = new ObjectVariableEntity();
        entity.setObjectPath("root.platform.devices.demo");
        entity.setName("temperature");
        when(variableRepository.findByHistoryEnabledTrue()).thenReturn(List.of(entity));

        Instant warmCutoff = service.warmCutoff();
        Instant exportDayStart = warmCutoff.truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS);
        Instant exportDayEnd = exportDayStart.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.MILLIS);
        Instant sampleTs = exportDayStart.plus(1, ChronoUnit.HOURS);

        when(queryStore.query(
                eq("root.platform.devices.demo"),
                eq("temperature"),
                eq("value"),
                eq(exportDayStart),
                eq(exportDayEnd),
                eq(100)
        )).thenReturn(List.of(new VariableHistoryService.VariableHistorySample(sampleTs, 21.5, null)));

        HistorianColdArchiveService.ColdArchiveRunResult result = service.exportEligibleDay();

        assertThat(result.ran()).isTrue();
        assertThat(result.exportedSeries()).isEqualTo(1);
        assertThat(result.exportedSamples()).isEqualTo(1);

        Path archiveRoot = tempDir.resolve("test-bucket").resolve("archive");
        try (var paths = Files.walk(archiveRoot)) {
            List<Path> parquetFiles = paths.filter(path -> path.toString().endsWith(".parquet")).toList();
            assertThat(parquetFiles).hasSize(1);
            assertThat(Files.size(parquetFiles.getFirst())).isGreaterThan(0);
        }
    }

    @Test
    void skipsWhenDisabled() {
        archiveProperties = new HistorianColdArchiveProperties(false, tempDir.toString(), 10, 100);
        service = new HistorianColdArchiveService(
                tierProperties,
                archiveProperties,
                variableRepository,
                queryStore,
                sink
        );

        var result = service.exportEligibleDay();

        assertThat(result.ran()).isFalse();
        assertThat(result.skipReason()).contains("disabled");
    }

    @Test
    void skipsSeriesWithNoSamples() {
        when(variableRepository.findByHistoryEnabledTrue()).thenReturn(List.of(new ObjectVariableEntity()));
        when(queryStore.query(any(), any(), any(), any(), any(), any(Integer.class))).thenReturn(List.of());

        var result = service.exportEligibleDay();

        assertThat(result.ran()).isTrue();
        assertThat(result.exportedSeries()).isZero();
        assertThat(result.skippedEmpty()).isEqualTo(1);
    }
}
