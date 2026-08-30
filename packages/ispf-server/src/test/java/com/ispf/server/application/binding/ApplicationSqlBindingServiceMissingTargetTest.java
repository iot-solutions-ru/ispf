package com.ispf.server.application.binding;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectTree;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationSqlBindingServiceMissingTargetTest {

    @Mock
    ApplicationSqlBindingStore store;
    @Mock
    ApplicationSchemaSession schemaSession;
    @Mock
    ApplicationDataStore dataStore;
    @Mock
    ObjectManager objectManager;
    @Mock
    AlertRuleService alertRuleService;
    @Mock
    BindingInvokeAuditService bindingAuditService;
    @Mock
    ObjectEntityMapper entityMapper;
    @Mock
    ApplicationSqlBindingEventIndex sqlBindingEventIndex;
    @Mock
    ObjectTree objectTree;

    private ApplicationSqlBindingService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationSqlBindingService(
                store,
                schemaSession,
                dataStore,
                objectManager,
                alertRuleService,
                bindingAuditService,
                entityMapper,
                sqlBindingEventIndex
        );
    }

    @Test
    void missingTargetObjectDoesNotRethrowAfterAudit() {
        UUID id = UUID.randomUUID();
        ApplicationSqlBindingStore.SqlBinding binding = new ApplicationSqlBindingStore.SqlBinding(
                id,
                "demo",
                "root.missing.target",
                "value",
                "SELECT 1 AS value",
                "on_schedule",
                30_000L,
                "value",
                null,
                null,
                true,
                null
        );

        when(dataStore.findApp("demo")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(schemaSession).runInSchema(anyString(), any(Runnable.class));
        when(dataStore.queryForList(anyString())).thenReturn(List.of(Map.of("value", 1.5)));
        doAnswer(invocation -> invocation.getArgument(0, Supplier.class).get())
                .when(schemaSession).callWithPlatformCatalog(any());
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(schemaSession).runWithPlatformCatalog(any(Runnable.class));
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath("root.missing.target")).thenReturn(Optional.empty());
        doThrow(new ObjectNotFoundException("root.missing.target"))
                .when(objectManager)
                .setSystemVariableValue(eq("root.missing.target"), eq("value"), any());
        when(entityMapper.auditDiff(isNull(), any())).thenReturn("{}");

        assertThatCode(() -> service.refreshBinding(binding)).doesNotThrowAnyException();

        verify(bindingAuditService).recordSql(
                eq("root.missing.target"),
                eq(id.toString()),
                eq("value"),
                anyString(),
                eq(false),
                eq(false),
                anyString(),
                anyLong(),
                any()
        );
        verify(store, never()).markRefreshed(any());
    }
}
