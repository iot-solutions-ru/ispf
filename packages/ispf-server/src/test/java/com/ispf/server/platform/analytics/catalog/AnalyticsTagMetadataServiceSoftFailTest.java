package com.ispf.server.platform.analytics.catalog;

import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsTagMetadataServiceSoftFailTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String MISSING = "root.missing.device";

    @Mock
    ObjectManager objectManager;
    @Mock
    HistorianRuleMetaService historianRuleMetaService;
    @Mock
    PlatformObject deviceNode;

    private AnalyticsTagMetadataService metadataService;

    @BeforeEach
    void setUp() {
        metadataService = new AnalyticsTagMetadataService(
                objectManager,
                historianRuleMetaService,
                new ObjectMapper()
        );
    }

    @Test
    void missingObjectMarksTagErrorAndContinues() {
        AnalyticsTagDefinition missingTag = tag(MISSING, "orphan-avg");
        AnalyticsTagDefinition validTag = new AnalyticsTagDefinition(
                HistorianTagPaths.encode(DEVICE, "avg-temp"),
                "avg",
                List.of(new AnalyticsSourceRef(MISSING, "avgValue", "value")),
                "5m",
                List.of("5m"),
                60_000L,
                true,
                true,
                "avgValue"
        );
        when(objectManager.require(MISSING)).thenThrow(new ObjectNotFoundException(MISSING));
        when(objectManager.require(DEVICE)).thenReturn(deviceNode);
        when(historianRuleMetaService.readRuleMeta(eq(deviceNode), eq("avg-temp")))
                .thenReturn(new HistorianRuleMetaService.RuleMeta("ok", "", null));

        List<String> missing = metadataService.propagateQuality(List.of(missingTag, validTag));

        assertThat(missing).containsExactly(missingTag.tagPath());
        verify(historianRuleMetaService).readRuleMeta(deviceNode, "avg-temp");
        verify(historianRuleMetaService).setQuality(validTag.tagPath(), AnalyticsTagMetadataService.QUALITY_UNCERTAIN);
        verify(historianRuleMetaService, never()).setQuality(eq(missingTag.tagPath()), eq(AnalyticsTagMetadataService.QUALITY_ERROR));
    }

    private static AnalyticsTagDefinition tag(String objectPath, String ruleId) {
        return new AnalyticsTagDefinition(
                HistorianTagPaths.encode(objectPath, ruleId),
                "avg",
                List.of(new AnalyticsSourceRef(objectPath, "temperature", "value")),
                "5m",
                List.of("5m"),
                60_000L,
                true,
                true,
                "avgValue"
        );
    }
}
