package com.ispf.server.application.bff;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.function.FunctionInvokeAccessService;
import com.ispf.server.function.FunctionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BffControllerUnknownWireProfileTest {

    private static final String HUB = "root.platform.hubs.room";

    private final FunctionService functionService = mock(FunctionService.class);
    private final FunctionInvokeAccessService invokeAccessService = mock(FunctionInvokeAccessService.class);
    private final BffController controller = new BffController(
            functionService,
            mock(ApplicationFunctionStore.class),
            new ObjectMapper(),
            invokeAccessService
    );

    @Test
    void unknownWireProfileIsRejectedBeforeInvoke() {
        assertThatThrownBy(() -> controller.invoke(
                new BffController.BffInvokeRequest(HUB, "ping", null, "NO_SUCH_PROFILE"),
                null
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(functionService, invokeAccessService);
    }

    @Test
    void nullAndBlankWireProfilesAreAccepted() {
        DataRecord output = DataRecord.single(DataSchema.builder("out").field("ok", FieldType.BOOLEAN).build(), Map.of("ok", true));
        when(functionService.invoke(eq(HUB), eq("ping"), any())).thenReturn(output);

        Map<String, Object> nullProfile = controller.invoke(
                new BffController.BffInvokeRequest(HUB, "ping", null, null),
                null
        );
        Map<String, Object> blankProfile = controller.invoke(
                new BffController.BffInvokeRequest(HUB, "ping", null, "  "),
                null
        );

        assertThat(nullProfile).containsEntry("error_code", "OK");
        assertThat(blankProfile).containsEntry("error_code", "OK");
        verify(invokeAccessService).requireDirectInvoke(HUB, "ping", null);
    }

    @Test
    void operatorWireProfileIsAccepted() {
        DataRecord output = DataRecord.single(DataSchema.builder("out").field("ok", FieldType.BOOLEAN).build(), Map.of("ok", true));
        when(functionService.invoke(eq(HUB), eq("ping"), any())).thenReturn(output);

        Map<String, Object> wire = controller.invoke(
                new BffController.BffInvokeRequest(HUB, "ping", null, IspfOperatorWireProfile.ID),
                null
        );

        assertThat(wire).containsEntry("wireProfile", IspfOperatorWireProfile.ID);
        assertThat(wire).containsEntry("error_code", "OK");
    }
}
