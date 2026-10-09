package com.ispf.server.binding;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.datasource.DataSourceSqlSession;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlBindingObjectServiceCatalogTest {

    private static final String HUB = "root.platform.singleton-blueprints.kpi-hub";

    @Mock
    ObjectManager objectManager;
    @Mock
    SystemObjectStructureService structureService;
    @Mock
    DataSourceSqlSession dataSourceSqlSession;
    @Mock
    TenantLocalDataAccessGuard tenantLocalDataAccessGuard;
    @Mock
    AutomationMetricsRecorder metricsRecorder;
    @Mock
    ObjectTree objectTree;

    private SqlBindingObjectService service;

    @BeforeEach
    void setUp() {
        service = new SqlBindingObjectService(
                objectManager,
                structureService,
                dataSourceSqlSession,
                tenantLocalDataAccessGuard,
                metricsRecorder
        );
    }

    @Test
    void refreshesReadTheCatalogWithoutRewritingIt() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(SqlBindingObjectService.BINDINGS_ROOT)).thenReturn(Optional.of(new PlatformObject(
                "id-bindings", SqlBindingObjectService.BINDINGS_ROOT, ObjectType.BINDINGS, "bindings", "", null)));
        when(objectTree.childrenOf(SqlBindingObjectService.BINDINGS_ROOT)).thenReturn(List.of());

        service.refreshScheduledBindings();
        service.refreshAfterFunctionCommit(HUB, "recalc");
        service.refreshAfterFunction(HUB, "recalc");

        verify(objectManager, never()).ensureSystemCatalogFolder(any(), any(), any());
        verify(objectManager, never()).updateInfo(any(), any(), any());
    }

    @Test
    void withoutTheCatalogTheListingsAreEmptyAndNothingIsCreated() {
        when(objectManager.tree()).thenReturn(new ObjectTree());

        assertThat(service.listEnabledForSchedule()).isEmpty();
        assertThat(service.listForFunctionSuccess(HUB, "recalc")).isEmpty();

        verify(objectManager, never()).ensureSystemCatalogFolder(any(), any(), any());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }
}
