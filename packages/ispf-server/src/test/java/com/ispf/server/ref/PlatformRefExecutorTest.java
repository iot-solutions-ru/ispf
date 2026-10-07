package com.ispf.server.ref;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;
import com.ispf.server.event.EventService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.VariableAclRequestContext;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformRefExecutorTest {

    @Mock
    ObjectManager objectManager;
    @Mock
    FunctionService functionService;
    @Mock
    EventService eventService;
    @Mock
    VariableMemberAccessService variableMemberAccessService;
    @Mock
    com.ispf.core.object.ObjectTree objectTree;

    @Test
    void readResolvesVariableField() {
        PlatformObject remote = deviceWithTemperature("root.platform.devices.remote", 42.0);
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath("root.platform.devices.remote")).thenReturn(Optional.of(remote));

        PlatformRefExecutor executor = new PlatformRefExecutor(
                objectManager,
                functionService,
                eventService,
                variableMemberAccessService
        );
        Optional<Object> value = executor.read(
                PlatformRefParser.parse("root.platform.devices.remote/temperature"),
                "root.platform.devices.local"
        );

        assertThat(value).contains(42.0);
    }

    @Test
    void firePublishesEvent() {
        PlatformRefExecutor executor = new PlatformRefExecutor(
                objectManager,
                functionService,
                eventService,
                variableMemberAccessService
        );
        PlatformRef evt = PlatformRefParser.parse("root.platform.devices.pump/evt/overload");
        executor.fire(evt, "root.platform.devices.local", null);
        verify(eventService).fire(
                eq("root.platform.devices.pump"),
                eq("overload"),
                nullable(DataRecord.class)
        );
    }

    @Test
    void callFailureNamesTheFunctionAndKeepsTheCause() {
        PlatformRefExecutor executor = new PlatformRefExecutor(
                objectManager,
                functionService,
                eventService,
                variableMemberAccessService
        );
        IllegalArgumentException missing = new IllegalArgumentException("Unknown function: boom");
        when(functionService.invoke(
                eq("root.platform.devices.pump"),
                eq("boom"),
                nullable(DataRecord.class)
        )).thenThrow(missing);

        assertThatThrownBy(() -> executor.call(
                PlatformRefParser.parse("root.platform.devices.pump/fn/boom"),
                "root.platform.devices.local",
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Function call failed: root.platform.devices.pump.boom: Unknown function: boom")
                .cause()
                .isSameAs(missing);
    }

    @Test
    void fireFailureNamesTheEventAndKeepsTheCause() {
        PlatformRefExecutor executor = new PlatformRefExecutor(
                objectManager,
                functionService,
                eventService,
                variableMemberAccessService
        );
        IllegalStateException missing = new IllegalStateException("Unknown event: overload");
        doThrow(missing).when(eventService).fire(
                eq("root.platform.devices.pump"),
                eq("overload"),
                nullable(DataRecord.class)
        );

        assertThatThrownBy(() -> executor.fire(
                PlatformRefParser.parse("root.platform.devices.pump/evt/overload"),
                "root.platform.devices.local",
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Event fire failed: root.platform.devices.pump.overload: Unknown event: overload")
                .cause()
                .isSameAs(missing);
    }

    @Test
    void memberReadOmitsVariableDeniedByAcl() {
        String path = "root.platform.devices.remote";
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                "n/a",
                List.of()
        );
        when(variableMemberAccessService.canRead(path, "temperature", authentication)).thenReturn(false);
        PlatformRefExecutor executor = new PlatformRefExecutor(
                objectManager,
                functionService,
                eventService,
                variableMemberAccessService
        );

        Optional<Object> value = VariableAclRequestContext.callAsMember(
                authentication,
                () -> executor.read(PlatformRefParser.parse(path + "/temperature"), null)
        );

        assertThat(value).isEmpty();
    }

    @Test
    void memberWriteRejectsVariableDeniedByAcl() {
        String path = "root.platform.devices.remote";
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                "n/a",
                List.of()
        );
        var denial = new ResponseStatusException(HttpStatus.FORBIDDEN, "denied");
        doThrow(denial)
                .when(variableMemberAccessService)
                .requireWrite(path, "temperature", authentication);
        PlatformRefExecutor executor = new PlatformRefExecutor(
                objectManager,
                functionService,
                eventService,
                variableMemberAccessService
        );

        assertThatThrownBy(() -> VariableAclRequestContext.callAsMember(
                authentication,
                () -> executor.write(PlatformRefParser.parse(path + "/temperature"), 43.0, null)
        )).isSameAs(denial);

        verify(variableMemberAccessService).requireWrite(path, "temperature", authentication);
        verifyNoInteractions(objectManager);
    }

    @Test
    void writeMissingObjectThrowsWithPath() {
        String path = "root.platform.devices.missing";
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(path)).thenReturn(Optional.empty());
        PlatformRefExecutor executor = executor();

        assertThatThrownBy(() -> executor.write(PlatformRefParser.parse(path + "/temperature"), 1.0, null))
                .isInstanceOf(ObjectNotFoundException.class)
                .hasMessage("Object not found: " + path);

        verify(objectManager, never()).setVariableValue(eq(path), eq("temperature"), nullable(DataRecord.class));
    }

    @Test
    void writeMissingVariableThrowsWithPathAndName() {
        String path = "root.platform.devices.remote";
        PlatformObject object = new PlatformObject(
                UUID.randomUUID().toString(),
                path,
                ObjectType.DEVICE,
                "device",
                "",
                null
        );
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(path)).thenReturn(Optional.of(object));
        PlatformRefExecutor executor = executor();

        assertThatThrownBy(() -> executor.write(PlatformRefParser.parse(path + "/temperature"), 1.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown variable: temperature on " + path);

        verify(objectManager, never()).setVariableValue(eq(path), eq("temperature"), nullable(DataRecord.class));
    }

    @Test
    void writeExistingVariableReturnsTrue() {
        String path = "root.platform.devices.remote";
        PlatformObject remote = deviceWithTemperature(path, 1.0);
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(path)).thenReturn(Optional.of(remote));
        PlatformRefExecutor executor = executor();

        boolean written = executor.write(PlatformRefParser.parse(path + "/temperature"), 43.0, null);

        assertThat(written).isTrue();
        verify(objectManager).setVariableValue(eq(path), eq("temperature"), nullable(DataRecord.class));
    }

    private PlatformRefExecutor executor() {
        return new PlatformRefExecutor(
                objectManager,
                functionService,
                eventService,
                variableMemberAccessService
        );
    }

    private static PlatformObject deviceWithTemperature(String path, double temp) {
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
        PlatformObject object = new PlatformObject(
                UUID.randomUUID().toString(),
                path,
                ObjectType.DEVICE,
                "device",
                "",
                null
        );
        object.addVariable(new Variable(
                "temperature",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", temp))
        ));
        return object;
    }
}
