package com.ispf.server.api;

import com.ispf.server.platform.PlatformBackupService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

class PlatformBackupControllerRateLimitTest {

    @Test
    void rejectsRepeatedExportFromSamePrincipal() {
        PlatformBackupService service = mock(PlatformBackupService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");
        when(service.exportSubtree("root.platform.models")).thenReturn(Map.of("nodeCount", 1));
        PlatformBackupController controller = new PlatformBackupController(service);

        controller.export(authentication, "root.platform.models");
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.export(authentication, "root.platform.models")
        );

        assertEquals(TOO_MANY_REQUESTS, error.getStatusCode());
        verify(service, times(1)).exportSubtree("root.platform.models");
    }
}
