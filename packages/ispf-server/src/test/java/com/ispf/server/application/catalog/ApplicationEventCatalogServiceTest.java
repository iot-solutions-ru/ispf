package com.ispf.server.application.catalog;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.IspfSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationEventCatalogServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ApplicationEventCatalogStore store;
    private IspfSecurityProperties securityProperties;
    private ApplicationEventCatalogService service;

    @BeforeEach
    void setUp() {
        store = mock(ApplicationEventCatalogStore.class);
        securityProperties = new IspfSecurityProperties();
        securityProperties.setRbacEnabled(true);
        service = new ApplicationEventCatalogService(store, objectMapper, securityProperties);
    }

    @Test
    void adminCanSubscribeToAdminOnlyEvent() {
        when(store.find("mes-reference", "mesRackOverTemp")).thenReturn(java.util.Optional.of(
                new ApplicationEventCatalogStore.EventCatalogEntry(
                        "mesRackOverTemp",
                        "[\"admin\"]",
                        null
                )
        ));

        assertTrue(service.canSubscribe("mes-reference", "mesRackOverTemp", List.of(IspfRoles.ADMIN)));
    }

    @Test
    void operatorCannotSubscribeToAdminOnlyEvent() {
        when(store.find("mes-reference", "mesRackOverTemp")).thenReturn(java.util.Optional.of(
                new ApplicationEventCatalogStore.EventCatalogEntry(
                        "mesRackOverTemp",
                        "[\"admin\"]",
                        null
                )
        ));

        assertFalse(service.canSubscribe("mes-reference", "mesRackOverTemp", List.of(IspfRoles.OPERATOR)));
    }

    @Test
    void filterSubscribableEventsSplitsAcceptedAndRejected() {
        when(store.find(eq("mes-reference"), eq("mesRackOverTemp"))).thenReturn(java.util.Optional.of(
                new ApplicationEventCatalogStore.EventCatalogEntry("mesRackOverTemp", "[\"admin\"]", null)
        ));
        when(store.find(eq("mes-reference"), eq("mesOrderUpdated"))).thenReturn(java.util.Optional.of(
                new ApplicationEventCatalogStore.EventCatalogEntry(
                        "mesOrderUpdated",
                        "[\"operator\",\"admin\"]",
                        null
                )
        ));

        Map<String, Object> result = service.filterSubscribableEvents(
                "mes-reference",
                List.of("mesRackOverTemp", "mesOrderUpdated"),
                List.of(IspfRoles.OPERATOR)
        );

        @SuppressWarnings("unchecked")
        List<String> accepted = (List<String>) result.get("accepted");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rejected = (List<Map<String, String>>) result.get("rejected");

        assertEquals(List.of("mesOrderUpdated"), accepted);
        assertEquals(1, rejected.size());
        assertEquals("mesRackOverTemp", rejected.get(0).get("event"));
    }

    @Test
    void rbacDisabledAllowsAllSubscriptions() {
        securityProperties.setRbacEnabled(false);
        when(store.find("mes-reference", "mesRackOverTemp")).thenReturn(java.util.Optional.of(
                new ApplicationEventCatalogStore.EventCatalogEntry("mesRackOverTemp", "[\"admin\"]", null)
        ));

        assertTrue(service.canSubscribe("mes-reference", "mesRackOverTemp", List.of()));
    }
}
