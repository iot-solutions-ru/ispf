package com.ispf.server.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatorAppControllerLaunchUrlTest {

    @Mock
    OperatorAppUiService service;
    @Mock
    OperatorStarterTemplatesService starterTemplatesService;

    @Test
    void saveUiReturnsTheLaunchUrlStatusInsteadOfAServerError() throws Exception {
        when(service.saveUi(
                "plant",
                "Plant",
                "root.platform.dashboards.main",
                List.of(Map.of("path", "root.platform.dashboards.main", "title", "Main")),
                null,
                null,
                null,
                null,
                "javascript:alert(1)",
                null
        )).thenThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "externalSpaUrl must be an http(s) URL or a path under /apps/"
        ));
        OperatorAppController controller = new OperatorAppController(service, starterTemplatesService);

        assertThatThrownBy(() -> controller.saveUi(
                "plant",
                new OperatorAppController.SaveOperatorUiRequest(
                        "Plant",
                        "root.platform.dashboards.main",
                        List.of(Map.of("path", "root.platform.dashboards.main", "title", "Main")),
                        null,
                        null,
                        null,
                        null,
                        "javascript:alert(1)",
                        null
                )
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
