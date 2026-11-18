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

class BffControllerMissingEnvelopeTest {

    private final FunctionService functionService = mock(FunctionService.class);
    private final ApplicationFunctionStore functionStore = mock(ApplicationFunctionStore.class);
    private final FunctionInvokeAccessService invokeAccessService = mock(FunctionInvokeAccessService.class);
    private final BffController controller = new BffController(
            functionService,
            functionStore,
            new ObjectMapper(),
            invokeAccessService
    );

    @Test
    void missingObjectPathIsBadRequest() {
        assertBadRequest(new BffController.BffInvokeRequest(null, "cc_getRoomLive", null, null));
    }

    @Test
    void missingFunctionNameIsBadRequest() {
        assertBadRequest(new BffController.BffInvokeRequest(
                "root.platform.devices.room",
                null,
                null,
                "ispf-operator-v1"
        ));
    }

    @Test
    void blankEnvelopeIsBadRequest() {
        assertBadRequest(new BffController.BffInvokeRequest("  ", "  ", null, null));
    }

    private void assertBadRequest(BffController.BffInvokeRequest request) {
        assertThatThrownBy(() -> controller.invoke(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(functionService, invokeAccessService);
    }
}
