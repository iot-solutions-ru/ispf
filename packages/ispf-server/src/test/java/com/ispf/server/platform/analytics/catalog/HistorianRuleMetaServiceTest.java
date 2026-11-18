package com.ispf.server.platform.analytics.catalog;

import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.binding.HistorianRuleMetaConstants;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistorianRuleMetaServiceTest {

    private static final String OBJECT = "root.platform.devices.demo";
    private static final String RULE = "avg-5m";
    private static final String TAG = HistorianTagPaths.encode(OBJECT, RULE);

    @Mock
    private ObjectManager objectManager;

    @Mock
    private PlatformObject node;

    @Mock
    private Variable metaVariable;

    @Test
    void recordEvaluationWritesLastEvalAtAndPreservesDisabledQuality() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String existing = mapper.writeValueAsString(Map.of(
                RULE, Map.of(
                        "quality", HistorianRuleMetaService.QUALITY_DISABLED,
                        "lastEvalStatus", "ok",
                        "lastEvalAt", "2026-01-01T00:00:00Z"
                )
        ));
        when(objectManager.require(OBJECT)).thenReturn(node);
        when(node.getVariable(HistorianRuleMetaConstants.META_VARIABLE)).thenReturn(Optional.of(metaVariable));
        when(metaVariable.value()).thenReturn(Optional.of(
                DataRecord.single(
                        DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                        Map.of("value", existing)
                )
        ));

        HistorianRuleMetaService service = new HistorianRuleMetaService(objectManager, mapper);
        Instant observed = Instant.parse("2026-11-18T12:00:00Z");
        service.recordEvaluation(TAG, "ok", observed);

        ArgumentCaptor<DataRecord> captor = ArgumentCaptor.forClass(DataRecord.class);
        verify(objectManager, atLeastOnce()).upsertSystemVariable(
                eq(OBJECT),
                eq(HistorianRuleMetaConstants.META_VARIABLE),
                any(DataSchema.class),
                captor.capture()
        );
        String json = String.valueOf(captor.getValue().firstRow().get("value"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> all = mapper.readValue(json, Map.class);
        assertThat(all.get(RULE).get("lastEvalAt")).isEqualTo(observed.toString());
        assertThat(all.get(RULE).get("lastEvalStatus")).isEqualTo("ok");
        assertThat(all.get(RULE).get("quality")).isEqualTo(HistorianRuleMetaService.QUALITY_DISABLED);
    }
}
