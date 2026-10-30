package com.ispf.server.application.bundle;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleManifest;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleSchedule;
import com.ispf.server.application.bundle.BundleTreeArtifactsApplier.BundleApplyLog;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.report.ApplicationReportService;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaService;
import com.ispf.server.application.schedule.PlatformSchedulerService;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BundleTreeArtifactsApplierDataSourceTest {

    @Mock
    private DataSourceObjectService dataSourceObjectService;
    @Mock
    private BlueprintRegistry blueprintRegistry;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectTree objectTree;
    @Mock
    private ApplicationBundleMetadataService metadataService;
    @Mock
    private ApplicationFunctionStore functionStore;
    @Mock
    private SqlBindingObjectService sqlBindingObjectService;
    @Mock
    private ApplicationReportService reportService;
    @Mock
    private ScheduleObjectService scheduleObjectService;
    @Mock
    private PlatformSchedulerService platformSchedulerService;
    @Mock
    private AnalyticsFormulaService analyticsFormulaService;
    @Mock
    private ApplicationEventCatalogService eventCatalogService;
    @Mock
    private BundleArtifactDeployers deployers;

    @Test
    void doesNotMarkDataSourceAppliedWhenEnsureDidNotCreateNode() {
        String appId = "robot-fleet-automation";
        String path = "root.platform.data-sources.robot-fleet-automation";
        when(dataSourceObjectService.pathForNodeName(appId)).thenReturn(path);
        doNothing().when(dataSourceObjectService).ensureDataSource(anyString(), anyString(), anyString(), anyString());
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(path)).thenReturn(Optional.empty());

        BundleTreeArtifactsApplier applier = newApplier();
        BundleApplyLog log = BundleApplyLog.empty();

        applier.apply(appId, emptyManifest("Robot Fleet"), false, path, log);

        assertThat(log.applied()).noneMatch(item -> item.startsWith("dataSource:"));
        assertThat(log.errors()).anyMatch(item -> item.contains("ensure did not create") && item.contains(path));
        verify(dataSourceObjectService).ensureDataSource(
                appId, "Robot Fleet", "app_robot_fleet_automation", "Package import data source");
    }

    @Test
    void marksDataSourceAppliedOnlyWhenNodeExists() {
        String appId = "demo-app";
        String path = "root.platform.data-sources.demo-app";
        when(dataSourceObjectService.pathForNodeName(appId)).thenReturn(path);
        doNothing().when(dataSourceObjectService).ensureDataSource(anyString(), anyString(), anyString(), anyString());
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(path)).thenReturn(Optional.of(
                new PlatformObject("demo-app", path, ObjectType.DATA_SOURCE, "Demo", "", null)
        ));

        BundleTreeArtifactsApplier applier = newApplier();
        BundleApplyLog log = BundleApplyLog.empty();

        applier.apply(appId, emptyManifest("Demo"), false, path, log);

        assertThat(log.applied()).containsExactly("dataSource:" + path);
        assertThat(log.errors()).isEmpty();
    }

    @Test
    void scheduleUpsertAlsoWritesLegacyPlatformSchedules() throws Exception {
        String appId = "robot-fleet-automation";
        String path = "root.platform.data-sources.robot-fleet-automation";
        when(dataSourceObjectService.pathForNodeName(appId)).thenReturn(path);
        doNothing().when(dataSourceObjectService).ensureDataSource(anyString(), anyString(), anyString(), anyString());
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(path)).thenReturn(Optional.of(
                new PlatformObject(appId, path, ObjectType.DATA_SOURCE, "Fleet", "", null)
        ));

        BundleSchedule schedule = new BundleSchedule(
                "fleet-tick",
                true,
                1000L,
                "invoke_function",
                java.util.Map.of("objectPath", "root.fleet.hub", "functionName", "tick")
        );
        BundleManifest manifest = new BundleManifest(
                "1.0.0",
                "Robot Fleet",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                java.util.List.of(schedule),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        BundleTreeArtifactsApplier applier = newApplier();
        BundleApplyLog log = BundleApplyLog.empty();
        applier.apply(appId, manifest, false, path, log);

        verify(scheduleObjectService).upsert(org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<PlatformSchedulerService.PlatformSchedule> captor =
                ArgumentCaptor.forClass(PlatformSchedulerService.PlatformSchedule.class);
        verify(platformSchedulerService).upsert(captor.capture());
        assertThat(captor.getValue().scheduleId()).isEqualTo("fleet-tick");
        assertThat(captor.getValue().appId()).isEqualTo(appId);
        assertThat(captor.getValue().enabled()).isTrue();
        assertThat(captor.getValue().intervalMs()).isEqualTo(1000L);
        assertThat(captor.getValue().actionType()).isEqualTo("invoke_function");
        assertThat(captor.getValue().actionJson()).contains("tick");
        assertThat(log.applied()).contains("schedule:fleet-tick");
    }

    private BundleTreeArtifactsApplier newApplier() {
        return new BundleTreeArtifactsApplier(
                dataSourceObjectService,
                blueprintRegistry,
                objectManager,
                metadataService,
                functionStore,
                sqlBindingObjectService,
                reportService,
                scheduleObjectService,
                platformSchedulerService,
                analyticsFormulaService,
                eventCatalogService,
                deployers,
                new ObjectMapper()
        );
    }

    private static BundleManifest emptyManifest(String displayName) {
        return new BundleManifest(
                "1.0.0",
                displayName,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
