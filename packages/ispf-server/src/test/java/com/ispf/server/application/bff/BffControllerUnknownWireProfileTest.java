package com.ispf.server.application.bff;

import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.function.FunctionInvokeAccessService;
import com.ispf.server.function.FunctionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class BffControllerUnknownWireProfileTest {

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
                new BffController.BffInvokeRequest(
                        "root.platform.devices.room",
                        "ping",
                        null,
                        "NO_SUCH_PROFILE"
                ),
                null
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(functionService, invokeAccessService);
    }
}
