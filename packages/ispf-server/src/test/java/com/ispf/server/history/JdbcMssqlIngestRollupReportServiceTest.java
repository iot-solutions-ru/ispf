package com.ispf.server.history;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.relational.MssqlDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcMssqlIngestRollupReportServiceTest {

    @Mock
    JdbcVariableSampleRollupStore rollupStore;
    @Mock
    JdbcMssqlHistorianRollupMaterializer materializer;
    @Mock
    VariableHistoryService variableHistoryService;
    @Mock
    ObjectManager objectManager;
    @Mock
    PlatformObject platformObject;
    @Mock
    Variable variable;

    @Test
    void unsupportedGrainFallsBackToObservedAtRaw() {
        MssqlRelationalProperties mssql = singleEngine();
        when(variableHistoryService.aggregate(any(), any(), any(), any(), any(), eq("15m"), anyInt()))
                .thenReturn(new VariableHistoryService.VariableHistoryAggregateResponse(
                        "root.d", "temp", "value", "15m", List.of(), "jdbc"
                ));

        var response = service(mssql).report("root.d", "temp", "value", Instant.EPOCH, Instant.now(), "15m", 100);
        assertThat(response.axis()).isEqualTo("observed_at");
        assertThat(response.source()).isEqualTo("raw_variable_samples");
        assertThat(response.fallbackReason()).isEqualTo("unsupported_grain");
    }

    @Test
    void closedBucketsBeforeCursorUseSampledAtRollups() {
        MssqlRelationalProperties mssql = singleEngine();
        Instant covered = Instant.parse("2026-02-01T12:00:00Z");
        Instant from = Instant.parse("2026-02-01T10:00:00Z");
        Instant to = Instant.parse("2026-02-01T14:00:00Z");
        when(objectManager.require("root.d")).thenReturn(platformObject);
        when(platformObject.getVariable("temp")).thenReturn(Optional.of(variable));
        when(variable.historyEnabled()).thenReturn(true);
        when(materializer.loadCoveredThrough(3_600)).thenReturn(Optional.of(covered));
        when(rollupStore.queryBucketsBefore(
                eq("root.d"), eq("temp"), eq("value"), eq(from), eq(covered), eq(3_600L), eq(50)
        )).thenReturn(List.of(
                new VariableHistoryService.VariableHistoryBucket(from, 1.0, 1.0, 1.0, 1)
        ));

        var response = service(mssql).report("root.d", "temp", "value", from, to, "1h", 50);
        assertThat(response.axis()).isEqualTo("sampled_at");
        assertThat(response.source()).isEqualTo("variable_sample_rollups");
        assertThat(response.fallbackReason()).isNull();
        assertThat(response.coveredThrough()).isEqualTo(covered);
        assertThat(response.buckets()).hasSize(1);
    }

    @Test
    void afterCursorReturnsEmptyRollupWithoutFallbackReason() {
        MssqlRelationalProperties mssql = singleEngine();
        Instant covered = Instant.parse("2026-02-01T12:00:00Z");
        Instant from = Instant.parse("2026-02-01T13:00:00Z");
        when(objectManager.require("root.d")).thenReturn(platformObject);
        when(platformObject.getVariable("temp")).thenReturn(Optional.of(variable));
        when(variable.historyEnabled()).thenReturn(true);
        when(materializer.loadCoveredThrough(3_600)).thenReturn(Optional.of(covered));

        var response = service(mssql).report("root.d", "temp", "value", from, from.plusSeconds(3600), "1h", 50);
        assertThat(response.source()).isEqualTo("variable_sample_rollups");
        assertThat(response.fallbackReason()).isNull();
        assertThat(response.buckets()).isEmpty();
        verify(rollupStore, never()).queryBucketsBefore(any(), any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void disabledHistoryReturnsEmpty() {
        MssqlRelationalProperties mssql = singleEngine();
        when(objectManager.require("root.d")).thenReturn(platformObject);
        when(platformObject.getVariable("temp")).thenReturn(Optional.of(variable));
        when(variable.historyEnabled()).thenReturn(false);

        var response = service(mssql).report("root.d", "temp", "value", Instant.EPOCH, Instant.now(), "1h", 50);
        assertThat(response.buckets()).isEmpty();
        assertThat(response.fallbackReason()).isNull();
        verify(materializer, never()).loadCoveredThrough(anyInt());
    }

    @Test
    void negativeLimitRejected() {
        assertThatThrownBy(() -> service(singleEngine())
                .report("root.d", "temp", "value", Instant.EPOCH, Instant.now(), "1h", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void materializedGrainSecondsOnlyHourAndDay() {
        assertThat(JdbcMssqlIngestRollupReportService.materializedGrainSeconds("1h")).contains(3_600);
        assertThat(JdbcMssqlIngestRollupReportService.materializedGrainSeconds("1d")).contains(86_400);
        assertThat(JdbcMssqlIngestRollupReportService.materializedGrainSeconds("5m")).isEmpty();
    }

    private JdbcMssqlIngestRollupReportService service(MssqlRelationalProperties mssql) {
        return new JdbcMssqlIngestRollupReportService(
                rollupStore,
                materializer,
                variableHistoryService,
                mssql,
                new MssqlDialect(),
                objectManager
        );
    }

    private static MssqlRelationalProperties singleEngine() {
        MssqlRelationalProperties mssql = new MssqlRelationalProperties();
        mssql.setSingleEngine(true);
        return mssql;
    }
}
