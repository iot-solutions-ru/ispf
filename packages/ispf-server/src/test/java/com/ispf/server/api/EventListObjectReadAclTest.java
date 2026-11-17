package com.ispf.server.api;

import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.server.event.EventService;
import com.ispf.server.eventfilter.EventFilterObjectService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventListObjectReadAclTest {

    private static final String OPEN = "root.devices.pump";
    private static final String CLOSED = "root.devices.vault";
    private static final String FILTER = "root.platform.event-filters.sample";

    private EventService eventService;
    private ObjectAccessService objectAccessService;
    private EventController eventController;
    private EventFilterController eventFilterController;
    private Authentication operator;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        objectAccessService = mock(ObjectAccessService.class);
        TenantScopeService tenantScopeService = mock(TenantScopeService.class);
        TenantVirtualRootService tenantVirtualRootService = mock(TenantVirtualRootService.class);
        when(tenantVirtualRootService.toCanonical(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantVirtualRootService.toVirtual(anyString(), any(Authentication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantScopeService.isPathVisible(any(), any())).thenReturn(true);
        operator = new UsernamePasswordAuthenticationToken("operator", "n/a");
        eventController = new EventController(
                eventService,
                objectAccessService,
                mock(ObjectManager.class),
                tenantScopeService,
                tenantVirtualRootService
        );
        eventFilterController = new EventFilterController(
                mock(EventFilterObjectService.class),
                eventService,
                tenantScopeService,
                tenantVirtualRootService,
                objectAccessService
        );
    }

    @Test
    void listByPathDeniesObjectTheCallerCannotRead() {
        deny(CLOSED);

        assertThatThrownBy(() -> eventController.list(CLOSED, 50, null, null, operator))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(eventService, never()).list(nullable(String.class), anyInt(), nullable(String.class), nullable(String.class));
    }

    @Test
    void listDropsEventsOfObjectsTheCallerCannotRead() {
        allow(OPEN);
        when(eventService.list(nullable(String.class), anyInt(), nullable(String.class), nullable(String.class)))
                .thenReturn(List.of(event(OPEN, "kept"), event(CLOSED, "hidden")));

        assertThat(eventController.list(OPEN, 50, null, null, operator))
                .extracting(ObjectEvent::eventName)
                .containsExactly("kept");
        assertThat(eventController.list(null, 50, null, null, operator))
                .extracting(ObjectEvent::eventName)
                .containsExactly("kept");
    }

    @Test
    void filteredListDeniesRequestedObjectAndDropsClosedEvents() {
        deny(CLOSED);
        assertThatThrownBy(() -> eventFilterController.apply(FILTER, CLOSED, 50, operator))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        allow(OPEN);
        when(eventService.list(nullable(String.class), anyInt(), nullable(String.class)))
                .thenReturn(List.of(event(OPEN, "kept"), event(CLOSED, "hidden")));
        assertThat(eventFilterController.apply(FILTER, null, 50, operator))
                .extracting(ObjectEvent::eventName)
                .containsExactly("kept");
    }

    private void allow(String path) {
        when(objectAccessService.canRead(eq(path), any())).thenReturn(true);
    }

    private void deny(String path) {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Read access denied for " + path))
                .when(objectAccessService)
                .requireRead(eq(path), any());
        when(objectAccessService.canRead(eq(path), any())).thenReturn(false);
    }

    private static ObjectEvent event(String path, String name) {
        return ObjectEvent.of(path, name, EventLevel.INFO, null, Instant.parse("2026-11-13T06:00:00Z"));
    }
}
