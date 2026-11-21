package com.ispf.server.history;

import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.relational.MssqlDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcMssqlObservedAtRollupQueryServiceTest {

    @Mock
    JdbcVariableSampleRollupStore rollupStore;
    @Mock
    JdbcMssqlObservedAtRollupMaterializer materializer;
    @Mock
    VariableHistoryQueryStore queryStore;

    @Test
    void flagOffReturnsEmptyWithoutReads() {
        MssqlRelationalProperties mssql = singleEngine();
        mssql.setObservedRollupReadEnabled(false);
        var result = service(mssql).tryAggregate(
                "root.d", "temp", "value",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-01T12:00:00Z"),
                Duration.ofHours(1),
                100
        );
        assertThat(result).isEmpty();
        verify(materializer, never()).loadSampledCoveredThrough(anyInt());
    }

    @Test
    void unsupportedGrainReturnsEmpty() {
        MssqlRelationalProperties mssql = enabled();
        var result = service(mssql).tryAggregate(
                "root.d", "temp", "value",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-01T12:00:00Z"),
                Duration.ofMinutes(5),
                100
        );
        assertThat(result).isEmpty();
    }

    @Test
    void missingCursorReturnsEmpty() {
        MssqlRelationalProperties mssql = enabled();
        when(materializer.loadSampledCoveredThrough(3_600)).thenReturn(Optional.empty());
        var result = service(mssql).tryAggregate(
                "root.d", "temp", "value",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-01T12:00:00Z"),
                Duration.ofHours(1),
                100
        );
        assertThat(result).isEmpty();
    }

    @Test
    void liveWindowStitchesRawTailAroundClosedMiddle() {
        MssqlRelationalProperties mssql = enabled();
        Instant covered = Instant.parse("2026-02-01T10:00:00Z");
        Instant from = Instant.parse("2026-02-01T07:30:00Z");
        Instant to = Instant.parse("2026-02-01T12:00:00Z");
        // servableExclusive = covered - 1h = 09:00; rollupFrom = ceil(07:30) = 08:00
        Instant rollupFrom = Instant.parse("2026-02-01T08:00:00Z");
        Instant servableExclusive = Instant.parse("2026-02-01T09:00:00Z");
        when(materializer.loadSampledCoveredThrough(3_600)).thenReturn(Optional.of(covered));
        when(rollupStore.queryObservedBucketsBefore(
                eq("root.d"), eq("temp"), eq("value"), eq(rollupFrom), eq(servableExclusive), eq(3_600L), eq(120)
        )).thenReturn(List.of(
                new VariableHistoryService.VariableHistoryBucket(rollupFrom, 2.0, 2.0, 2.0, 2)
        ));
        when(queryStore.aggregateBuckets(
                eq("root.d"), eq("temp"), eq("value"),
                eq(servableExclusive), eq(to), eq(Duration.ofHours(1)), eq(120)
        )).thenReturn(List.of(
                new VariableHistoryService.VariableHistoryBucket(servableExclusive, 3.0, 3.0, 3.0, 3),
                new VariableHistoryService.VariableHistoryBucket(
                        Instant.parse("2026-02-01T10:00:00Z"), 4.0, 4.0, 4.0, 4
                )
        ));
        when(queryStore.aggregateBuckets(
                eq("root.d"), eq("temp"), eq("value"),
                eq(from), eq(rollupFrom.minusNanos(100)), eq(Duration.ofHours(1)), eq(120)
        )).thenReturn(List.of(
                new VariableHistoryService.VariableHistoryBucket(
                        Instant.parse("2026-02-01T07:00:00Z"), 1.0, 1.0, 1.0, 1
                ),
                // Simulate DATETIME2 leak at boundary — must be filtered out.
                new VariableHistoryService.VariableHistoryBucket(rollupFrom, 9.0, 9.0, 9.0, 9)
        ));

        var result = service(mssql).tryAggregate(
                "root.d", "temp", "value", from, to, Duration.ofHours(1), 120
        );
        assertThat(result).isPresent();
        assertThat(result.get().dataSource()).isEqualTo("observed_rollup");
        assertThat(result.get().buckets()).extracting(VariableHistoryService.VariableHistoryBucket::ts)
                .containsExactly(
                        Instant.parse("2026-02-01T07:00:00Z"),
                        rollupFrom,
                        servableExclusive,
                        Instant.parse("2026-02-01T10:00:00Z")
                );
        assertThat(result.get().buckets()).noneMatch(b -> b.avg() != null && b.avg() == 9.0);
    }

    @Test
    void alignedFromSkipsRawHead() {
        MssqlRelationalProperties mssql = enabled();
        Instant covered = Instant.parse("2026-02-01T10:00:00Z");
        Instant from = Instant.parse("2026-02-01T08:00:00Z");
        Instant to = Instant.parse("2026-02-01T09:00:00Z");
        Instant servableExclusive = Instant.parse("2026-02-01T09:00:00Z");
        when(materializer.loadSampledCoveredThrough(3_600)).thenReturn(Optional.of(covered));
        when(rollupStore.queryObservedBucketsBefore(
                eq("root.d"), eq("temp"), eq("value"), eq(from), eq(servableExclusive), eq(3_600L), eq(50)
        )).thenReturn(List.of(
                new VariableHistoryService.VariableHistoryBucket(from, 1.0, 1.0, 1.0, 1)
        ));
        when(queryStore.aggregateBuckets(
                eq("root.d"), eq("temp"), eq("value"),
                eq(servableExclusive), eq(to), eq(Duration.ofHours(1)), eq(50)
        )).thenReturn(List.of());

        var result = service(mssql).tryAggregate(
                "root.d", "temp", "value", from, to, Duration.ofHours(1), 50
        );
        assertThat(result).isPresent();
        verify(queryStore, never()).aggregateBuckets(
                any(), any(), any(), eq(from), any(), any(), anyInt()
        );
    }

    @Test
    void noClosedMiddleReturnsEmpty() {
        MssqlRelationalProperties mssql = enabled();
        Instant covered = Instant.parse("2026-02-01T08:00:00Z");
        // servableExclusive = 07:00; from=07:00 → rollupFrom=07:00; to=07:30 → rollupToExclusive=07:00
        when(materializer.loadSampledCoveredThrough(3_600)).thenReturn(Optional.of(covered));
        var result = service(mssql).tryAggregate(
                "root.d", "temp", "value",
                Instant.parse("2026-02-01T07:00:00Z"),
                Instant.parse("2026-02-01T07:30:00Z"),
                Duration.ofHours(1),
                50
        );
        assertThat(result).isEmpty();
        verify(rollupStore, never()).queryObservedBucketsBefore(any(), any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void dayGrainUses86400() {
        MssqlRelationalProperties mssql = enabled();
        Instant covered = Instant.parse("2026-02-10T00:00:00Z");
        Instant from = Instant.parse("2026-02-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-05T00:00:00Z");
        Instant servableExclusive = Instant.parse("2026-02-09T00:00:00Z");
        when(materializer.loadSampledCoveredThrough(86_400)).thenReturn(Optional.of(covered));
        when(rollupStore.queryObservedBucketsBefore(
                eq("root.d"), eq("temp"), eq("value"), eq(from), eq(to), eq(86_400L), eq(30)
        )).thenReturn(List.of(
                new VariableHistoryService.VariableHistoryBucket(from, 1.0, 1.0, 1.0, 1)
        ));
        when(queryStore.aggregateBuckets(
                eq("root.d"), eq("temp"), eq("value"),
                eq(to), eq(to), eq(Duration.ofDays(1)), eq(30)
        )).thenReturn(List.of());

        var result = service(mssql).tryAggregate(
                "root.d", "temp", "value", from, to, Duration.ofDays(1), 30
        );
        assertThat(result).isPresent();
        // closed to == floor(to) → middle ends at to; servableExclusive is later so rollupToExclusive=to
        assertThat(servableExclusive).isAfter(to);
        verify(materializer).loadSampledCoveredThrough(86_400);
    }

    @Test
    void singleEngineOffReturnsEmpty() {
        MssqlRelationalProperties mssql = new MssqlRelationalProperties();
        mssql.setSingleEngine(false);
        mssql.setObservedRollupReadEnabled(true);
        assertThat(service(mssql).tryAggregate(
                "root.d", "temp", "value",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-01T12:00:00Z"),
                Duration.ofHours(1),
                10
        )).isEmpty();
    }

    @Test
    void sqlErrorFallsBackToEmptyOptional() {
        MssqlRelationalProperties mssql = enabled();
        when(materializer.loadSampledCoveredThrough(3_600)).thenReturn(Optional.of(Instant.parse("2026-02-01T10:00:00Z")));
        when(rollupStore.queryObservedBucketsBefore(any(), any(), any(), any(), any(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("boom"));
        var result = service(mssql).tryAggregate(
                "root.d", "temp", "value",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-01T12:00:00Z"),
                Duration.ofHours(1),
                50
        );
        assertThat(result).isEmpty();
    }

    @Test
    void mergeNewestKeepsNewestN() {
        var a = new VariableHistoryService.VariableHistoryBucket(Instant.parse("2026-01-01T01:00:00Z"), 1.0, 1.0, 1.0, 1);
        var b = new VariableHistoryService.VariableHistoryBucket(Instant.parse("2026-01-01T02:00:00Z"), 2.0, 2.0, 2.0, 2);
        var c = new VariableHistoryService.VariableHistoryBucket(Instant.parse("2026-01-01T03:00:00Z"), 3.0, 3.0, 3.0, 3);
        assertThat(JdbcMssqlObservedAtRollupQueryService.mergeNewest(List.of(a), List.of(b), List.of(c), 2))
                .extracting(VariableHistoryService.VariableHistoryBucket::ts)
                .containsExactly(b.ts(), c.ts());
    }

    private JdbcMssqlObservedAtRollupQueryService service(MssqlRelationalProperties mssql) {
        return new JdbcMssqlObservedAtRollupQueryService(
                rollupStore, materializer, queryStore, mssql, new MssqlDialect()
        );
    }

    private static MssqlRelationalProperties enabled() {
        MssqlRelationalProperties mssql = singleEngine();
        mssql.setObservedRollupReadEnabled(true);
        return mssql;
    }

    private static MssqlRelationalProperties singleEngine() {
        MssqlRelationalProperties mssql = new MssqlRelationalProperties();
        mssql.setSingleEngine(true);
        return mssql;
    }
}
