package com.ispf.server.platform.analytics.engine;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.AnalyticsBlueprintBootstrap;
import com.ispf.server.platform.analytics.AssetAnalyticsService;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagCatalogEntry;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagMetadataService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import com.ispf.plugin.blueprint.BlueprintRegistry;
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
    private AssetAnalyticsService assetAnalyticsService;

    @Autowired
    private AnalyticsEngineService engineService;

    @Autowired
    private AnalyticsTagCatalogService catalogService;

    @Autowired
    private AnalyticsTagMetadataService metadataService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Autowired
    private BlueprintRegistry blueprintRegistry;

    @Autowired
    private BlueprintApplicationService blueprintApplicationService;

    @Test
    void lineageGraphShowsThreeTagChainAndPropagatesUncertainQuality() {
        assetAnalyticsService.ensureCatalog();
        seedHistorianSamples(24.0);

        String tagA = createDerivedTag("analytics-catalog-a", SENSOR, "temperature");
        String tagB = createDerivedTag("analytics-catalog-b", tagA, "derivedValue");
        String tagC = createDerivedTag("analytics-catalog-c", tagB, "derivedValue");

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

    private void disableTag(String path) {
        objectManager.setVariableValue(
                path,
                "analyticsTagEnabled",
                DataRecord.single(
                        DataSchema.builder("booleanValue").field("value", FieldType.BOOLEAN).build(),
                        Map.of("value", false)
                )
        );
        objectManager.setVariableValue(
                path,
                "analyticsQuality",
                stringRecord(AnalyticsTagMetadataService.QUALITY_DISABLED)
        );
        objectManager.persistNodeTree(path);
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

    private String createDerivedTag(String nodeName, String sourcePath, String sourceVariable) {
        String parent = "root.platform.devices";
        String path = parent + "." + nodeName;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(parent, nodeName, ObjectType.DEVICE, nodeName, "analytics catalog", null);
        }
        var model = blueprintRegistry.findByName(AnalyticsBlueprintBootstrap.ROLLING_AVG_MODEL).orElseThrow();
        blueprintApplicationService.applyBlueprintWithRules(model, path, Map.of());
        objectManager.setVariableValue(path, "sourcePath", stringRecord(sourcePath));
        objectManager.setVariableValue(path, "sourceVariable", stringRecord(sourceVariable));
        objectManager.setVariableValue(path, "sourceField", stringRecord("value"));
        objectManager.setVariableValue(path, "windowBucket", stringRecord("1h"));
        PlatformObject node = objectManager.require(path);
        metadataService.ensureTagMetadata(node);
        objectManager.persistNodeTree(path);
        return path;
    }

    private static DataRecord stringRecord(String value) {
        return DataRecord.single(
                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        );
    }
}
