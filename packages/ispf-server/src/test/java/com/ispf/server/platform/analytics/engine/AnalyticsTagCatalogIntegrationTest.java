package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.HistorianComputationTestSupport;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagCatalogEntry;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagMetadataService;
import com.ispf.server.history.VariableHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnalyticsTagCatalogIntegrationTest {

    private static final String SENSOR = "root.platform.devices.demo-sensor-01";

    @Autowired
    private AnalyticsEngineService engineService;

    @Autowired
    private AnalyticsTagCatalogService catalogService;

    @Autowired
    private AnalyticsTagMetadataService metadataService;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Test
    void lineageGraphShowsThreeTagChainAndPropagatesUncertainQuality() {
        seedHistorianSamples(24.0);

        String tagA = createHistorianTag("analytics-catalog-a", SENSOR, "temperature", "derived-a");
        String tagB = createHistorianTag("analytics-catalog-b", tagAObjectPath(tagA), "derived-a", "derived-b");
        String tagC = createHistorianTag("analytics-catalog-c", tagAObjectPath(tagB), "derived-b", "derived-c");

        engineService.evaluateAllEnabled();

        AnalyticsTagCatalogEntry tagCEntry = catalogService.getCatalogEntry(tagC);
        assertThat(tagCEntry.upstreamTagPaths()).containsExactly(tagB);
        assertThat(tagCEntry.downstreamTagPaths()).isEmpty();
        assertThat(tagCEntry.lineage().nodes()).extracting("id")
                .contains(tagA, tagB, tagC);
        assertThat(tagCEntry.lineage().edges()).isNotEmpty();
        assertThat(tagCEntry.qualityStatus()).isEqualTo(AnalyticsTagMetadataService.QUALITY_OK);

        disableTag(tagA);
        metadataService.propagateQuality(catalogService.listAllTagDefinitions());

        assertThat(catalogService.getCatalogEntry(tagB).qualityStatus())
                .isEqualTo(AnalyticsTagMetadataService.QUALITY_UNCERTAIN);
        assertThat(catalogService.getCatalogEntry(tagC).qualityStatus())
                .isEqualTo(AnalyticsTagMetadataService.QUALITY_UNCERTAIN);
    }

    private void disableTag(String tagPath) {
        bindingRulesService.saveRules(
                HistorianTagPaths.objectPath(tagPath),
                bindingRulesService.listRules(HistorianTagPaths.objectPath(tagPath)).stream()
                        .map(rule -> rule.id().equals(HistorianTagPaths.ruleId(tagPath))
                                ? new com.ispf.core.binding.BindingRule(
                                        rule.id(),
                                        rule.name(),
                                        false,
                                        rule.order(),
                                        rule.kind(),
                                        rule.activators(),
                                        rule.condition(),
                                        rule.expression(),
                                        rule.target(),
                                        rule.windowBucket(),
                                        rule.rollupBuckets()
                                )
                                : rule)
                        .toList()
        );
        objectManager.persistNodeTree(HistorianTagPaths.objectPath(tagPath));
    }

    private void seedHistorianSamples(double value) {
        for (int i = 0; i < 3; i++) {
            objectManager.setVariableValue(
                    SENSOR,
                    "temperature",
                    DataRecord.single(
                            DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build(),
                            Map.of("value", value)
                    )
            );
            variableHistoryService.recordVariableUpdate(SENSOR, "temperature");
        }
    }

    private String createHistorianTag(
            String nodeName,
            String sourcePath,
            String sourceVariable,
            String outputVariable
    ) {
        String devicePath = HistorianComputationTestSupport.ensureDevice(
                objectManager,
                "root.platform.devices",
                nodeName
        );
        String ruleId = nodeName + "-rule";
        HistorianComputationTestSupport.upsertRollingAvgRule(
                bindingRulesService,
                devicePath,
                ruleId,
                sourcePath,
                sourceVariable,
                outputVariable,
                "1h"
        );
        return HistorianTagPaths.encode(devicePath, ruleId);
    }

    private static String tagAObjectPath(String tagPath) {
        return HistorianTagPaths.objectPath(tagPath);
    }
}
