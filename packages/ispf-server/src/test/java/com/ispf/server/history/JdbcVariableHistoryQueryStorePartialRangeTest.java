package com.ispf.server.history;

import com.ispf.server.persistence.VariableSampleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcVariableHistoryQueryStorePartialRangeTest {

    @Mock
    private VariableSampleRepository sampleRepository;

    @Mock
    private com.ispf.server.relational.store.VariableSampleBucketQuery bucketQuery;

    @Test
    void fromOnlyUsesTheLowerBound() {
        Instant from = Instant.parse("2099-01-01T00:00:00Z");
        when(sampleRepository.findByEffectiveTimestampFromOrderByEffectiveTimestampDesc(
                eq("root.room"), eq("temperature"), eq("value"), eq(from), any()
        )).thenReturn(List.of());

        List<VariableHistoryService.VariableHistorySample> samples = store().query(
                "root.room", "temperature", "value", from, null, 10
        );

        assertThat(samples).isEmpty();
        verify(sampleRepository, never()).findByObjectPathAndVariableNameAndFieldNameOrderByEffectiveTimestampDesc(
                any(), any(), any(), any()
        );
    }

    @Test
    void toOnlyUsesTheUpperBound() {
        Instant to = Instant.parse("2020-01-02T00:00:00Z");
        when(sampleRepository.findByEffectiveTimestampUntilOrderByEffectiveTimestampDesc(
                eq("root.room"), eq("temperature"), eq("value"), eq(to), any()
        )).thenReturn(List.of());

        List<VariableHistoryService.VariableHistorySample> samples = store().query(
                "root.room", "temperature", "value", null, to, 10
        );

        assertThat(samples).isEmpty();
        verify(sampleRepository, never()).findByObjectPathAndVariableNameAndFieldNameOrderByEffectiveTimestampDesc(
                any(), any(), any(), any()
        );
    }

    private JdbcVariableHistoryQueryStore store() {
        return new JdbcVariableHistoryQueryStore(sampleRepository, bucketQuery, null, null);
    }
}
