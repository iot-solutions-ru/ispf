package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService;
import com.ispf.server.platform.analytics.formula.BindingFormulaResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class BindingRulesServiceMissingActivatorEventTest {

    private static final String PATH = "root.platform.devices.event-proof";

    @Mock
    private ObjectManager objectManager;

    @Mock
    private PlatformObject device;

    @Mock
    private BindingFormulaResolver bindingFormulaResolver;

    @Mock
    private BindingPeriodicScheduleRegistry periodicScheduleRegistry;

    @Mock
    private BindingPeriodicScheduler periodicScheduler;

    @Mock
    private AnalyticsTagCatalogService tagCatalogService;

    @Test
    void missingActivatorEventIsRejectedBeforeSave() {
        when(objectManager.require(PATH)).thenReturn(device);
        when(device.getVariable(any())).thenReturn(Optional.empty());
        when(device.events()).thenReturn(Map.of());
        when(bindingFormulaResolver.resolve(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().saveRules(PATH, List.of(rule("noSuchEvent")))
        );

        assertEquals("Unknown event: noSuchEvent", error.getMessage());
        verify(objectManager, never()).upsertSystemVariable(any(), any(), any(), any());
    }

    @Test
    void existingActivatorEventIsSaved() {
        Variable temp = mock(Variable.class);
        when(temp.schema()).thenReturn(DataSchema.builder("temp").field("value", FieldType.DOUBLE).build());
        when(objectManager.require(PATH)).thenReturn(device);
        when(device.getVariable(BindingRulesConstants.RULES_VARIABLE)).thenReturn(Optional.empty());
        when(device.getVariable("temp")).thenReturn(Optional.of(temp));
        when(device.events()).thenReturn(Map.of("probe", mock(EventDescriptor.class)));
        when(bindingFormulaResolver.resolve(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().saveRules(PATH, List.of(rule("probe")));

        verify(objectManager).upsertSystemVariable(any(), any(), any(), any());
    }

    private BindingRulesService service() {
        return new BindingRulesService(
                objectManager,
                new ObjectMapper(),
                periodicScheduleRegistry,
                periodicScheduler,
                null,
                null,
                null,
                bindingFormulaResolver,
                tagCatalogService
        );
    }

    private static BindingRule rule(String onEvent) {
        return new BindingRule(
                "event-proof",
                "event-proof",
                false,
                0,
                new BindingActivators(false, List.of(), onEvent, 0),
                "",
                "1",
                new BindingTarget("temp", "value")
        );
    }
}
