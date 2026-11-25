package com.ispf.server.application;

import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.application.binding.ApplicationSqlBindingStore;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.application.catalog.ApplicationEventCatalogStore;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.report.ApplicationReportService;
import com.ispf.server.config.IspfSecurityProperties;
import com.ispf.server.report.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationMissingListTest {

    @Test
    void unknownApplicationIsRejectedBeforeEachListIsRead() {
        ApplicationDataStore apps = appsMissing("no-such-app");

        ApplicationEventCatalogStore events = mock(ApplicationEventCatalogStore.class);
        ApplicationEventCatalogService eventCatalog = new ApplicationEventCatalogService(
                events, new ObjectMapper(), new IspfSecurityProperties(), apps);
        assertUnknown(() -> eventCatalog.listEvents("no-such-app"));
        verify(events, never()).listForApp(any());

        ApplicationBundleSnapshotStore snapshots = mock(ApplicationBundleSnapshotStore.class);
        ApplicationBundleDeployService deploy = new ApplicationBundleDeployService(
                new ApplicationDataService(apps, null, null, null, null),
                null,
                snapshots,
                null, null, null, null, null, null, null, null, null, null, null
        );
        assertUnknown(() -> deploy.deployHistory("no-such-app"));
        verify(snapshots, never()).listHistory(any());

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformSqlCatalog catalog = mock(PlatformSqlCatalog.class);
        when(catalog.table("application_functions")).thenReturn("application_functions");
        ApplicationFunctionStore functions = new ApplicationFunctionStore(jdbc, null, catalog, null, apps);
        assertUnknown(() -> functions.listVersions("no-such-app", "root.platform.devices.demo", "ping"));
        verify(jdbc, never()).query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any());

        ApplicationSqlBindingStore bindings = mock(ApplicationSqlBindingStore.class);
        ApplicationSqlBindingService bindingService = new ApplicationSqlBindingService(
                bindings, null, apps, null, null, null, null, null, null);
        assertUnknown(() -> bindingService.list("no-such-app"));
        verify(bindings, never()).listByApp(any());

        ReportService reports = mock(ReportService.class);
        ApplicationReportService reportService = new ApplicationReportService(reports, null, null, apps);
        assertUnknown(() -> reportService.list("no-such-app"));
        verify(reports, never()).listByDataSource(any());
    }

    @Test
    void registeredApplicationStillListsReports() {
        ApplicationDataStore apps = mock(ApplicationDataStore.class);
        when(apps.findApp("list-proof")).thenReturn(Optional.of(Map.of("app_id", "list-proof")));
        when(apps.requireApp("list-proof")).thenCallRealMethod();
        ReportService reports = mock(ReportService.class);
        com.ispf.server.datasource.DataSourceObjectService dataSources =
                mock(com.ispf.server.datasource.DataSourceObjectService.class);
        when(dataSources.pathForNodeName("list-proof")).thenReturn("root.platform.data-sources.list-proof");
        when(reports.listByDataSource("root.platform.data-sources.list-proof")).thenReturn(List.of());
        ApplicationReportService reportService = new ApplicationReportService(reports, null, dataSources, apps);
        assertEquals(List.of(), reportService.list("list-proof"));
    }

    private static ApplicationDataStore appsMissing(String appId) {
        ApplicationDataStore apps = mock(ApplicationDataStore.class);
        when(apps.findApp(appId)).thenReturn(Optional.empty());
        when(apps.requireApp(appId)).thenCallRealMethod();
        return apps;
    }

    private static void assertUnknown(org.junit.jupiter.api.function.Executable call) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, call);
        assertEquals("Unknown application: no-such-app", ex.getMessage());
    }
}
