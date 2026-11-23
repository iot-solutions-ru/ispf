package com.ispf.server.history;

import com.ispf.server.persistence.VariableSampleBucketAggregate;
import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.relational.H2Dialect;
import com.ispf.server.relational.MysqlDialect;
import com.ispf.server.relational.RelationalDialect;
import com.ispf.server.relational.store.VariableSampleBucketQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcVariableHistoryQueryStoreDialectPathTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-01T01:00:00Z");

    @Mock
    private VariableSampleRepository sampleRepository;

    @Mock
    private VariableSampleBucketQuery bucketQuery;

    @Test
    void mysqlAggregatesInSqlNotInTheJvm() {
        Instant bucketStart = Instant.parse("2026-01-01T00:05:00Z");
        when(bucketQuery.aggregateBuckets("root.room", "temperature", "value", FROM, TO, 300L, 12))
                .thenReturn(List.of(bucket(bucketStart, 21.5, 20.0, 23.0, 4L)));

        List<VariableHistoryService.VariableHistoryBucket> buckets = store(new MysqlDialect())
                .aggregateBuckets("root.room", "temperature", "value", FROM, TO, Duration.ofMinutes(5), 12);

        assertThat(buckets).containsExactly(
                new VariableHistoryService.VariableHistoryBucket(bucketStart, 21.5, 20.0, 23.0, 4));
        verify(sampleRepository, never()).findByEffectiveTimestampBetweenOrderByEffectiveTimestampAsc(
                anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void h2StillAggregatesInTheJvm() {
        when(sampleRepository.findByEffectiveTimestampBetweenOrderByEffectiveTimestampAsc(
                anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(store(new H2Dialect())
                .aggregateBuckets("root.room", "temperature", "value", FROM, TO, Duration.ofMinutes(5), 12))
                .isEmpty();
        verify(bucketQuery, never()).aggregateBuckets(
                anyString(), anyString(), anyString(), any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    private JdbcVariableHistoryQueryStore store(RelationalDialect dialect) {
        return new JdbcVariableHistoryQueryStore(sampleRepository, bucketQuery, dialect, null);
    }

    private static VariableSampleBucketAggregate bucket(Instant start, double avg, double min, double max, long count) {
        return new VariableSampleBucketAggregate() {
            @Override
            public Instant getBucketStart() {
                return start;
            }

            @Override
            public Double getAvgVal() {
                return avg;
            }

            @Override
            public Double getMinVal() {
                return min;
            }

            @Override
            public Double getMaxVal() {
                return max;
            }

            @Override
            public Long getSampleCount() {
                return count;
            }
        };
    }
}
