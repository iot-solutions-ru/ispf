package com.ispf.server.api;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.event.EventService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventControllerListMissingObjectTest {

    private static final String MISSING = "root.platform.devices.NO_SUCH";
    private static final String PRESENT = "root.platform.devices.present";

    private EventService eventService;
    private ObjectManager objectManager;
    private EventController controller;
    private Authentication operator;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        objectManager = mock(ObjectManager.class);
        TenantVirtualRootService tenantVirtualRootService = mock(TenantVirtualRootService.class);
        when(tenantVirtualRootService.toCanonical(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        operator = new UsernamePasswordAuthenticationToken("operator", "n/a");
        controller = new EventController(
                eventService,
                mock(ObjectAccessService.class),
                objectManager,
                mock(TenantScopeService.class),
                tenantVirtualRootService
        );
    }

    @Test
    void missingObjectIsNotAnEmptyEventList() {
        when(objectManager.require(MISSING)).thenThrow(new ObjectNotFoundException(MISSING));

        assertThatThrownBy(() -> controller.list(MISSING, 50, null, "true", operator))
                .isInstanceOf(ObjectNotFoundException.class)
                .hasMessage("Object not found: " + MISSING);
        verify(eventService, never()).list(nullable(String.class), anyInt(), nullable(String.class), nullable(String.class));
    }

    @Test
    void existingObjectWithoutEventsStaysEmpty() {
        when(objectManager.require(PRESENT)).thenReturn(mock(PlatformObject.class));
        when(eventService.list(eq(PRESENT), eq(50), nullable(String.class), nullable(String.class))).thenReturn(List.of());

        assertThat(controller.list(PRESENT, 50, null, null, operator)).isEmpty();
        verify(objectManager).require(PRESENT);
    }

    @Test
    void omittedPathDoesNotRequireAnObject() {
        when(eventService.list(nullable(String.class), anyInt(), nullable(String.class), nullable(String.class)))
                .thenReturn(List.of());

        assertThat(controller.list(null, 50, null, null, operator)).isEmpty();
        verify(objectManager, never()).require(any());
    }
}
