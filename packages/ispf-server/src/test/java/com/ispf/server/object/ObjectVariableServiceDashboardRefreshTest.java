package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectVariableServiceDashboardRefreshTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformObject dashboard;

    @Test
    void nonPositiveDashboardRefreshIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.require("root.platform.dashboards.refresh-proof")).thenReturn(dashboard);
        when(dashboard.type()).thenReturn(ObjectType.DASHBOARD);
        ObjectVariableService service = new ObjectVariableService(
                objectManager, null, null, null, null, null, null, null
        );

        IllegalArgumentException negative = assertThrows(
                IllegalArgumentException.class,
                () -> service.setVariableValue(
                        "root.platform.dashboards.refresh-proof",
                        "refreshIntervalMs",
                        record(-1)
                )
        );
        IllegalArgumentException zero = assertThrows(
                IllegalArgumentException.class,
                () -> service.setVariableValue(
                        "root.platform.dashboards.refresh-proof",
                        "refreshIntervalMs",
                        record(0)
                )
        );

        assertEquals("refreshIntervalMs must be positive", negative.getMessage());
        assertEquals("refreshIntervalMs must be positive", zero.getMessage());
        verify(dashboard, never()).setVariableValue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(objectManager, never()).persistVariable(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static DataRecord record(int refreshIntervalMs) {
        DataSchema schema = DataSchema.builder("refreshIntervalMs")
                .field("value", FieldType.INTEGER)
                .build();
        return DataRecord.single(schema, Map.of("value", refreshIntervalMs));
    }
}
