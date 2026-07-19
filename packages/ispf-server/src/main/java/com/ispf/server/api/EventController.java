package com.ispf.server.api;

import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.ObjectEvent;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.event.EventService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import java.time.Instant;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;
    private final ObjectAccessService objectAccessService;
    private final ObjectManager objectManager;
    private final TenantScopeService tenantScopeService;
    private final TenantVirtualRootService tenantVirtualRootService;

    public EventController(
            EventService eventService,
            ObjectAccessService objectAccessService,
            ObjectManager objectManager,
            TenantScopeService tenantScopeService,
            TenantVirtualRootService tenantVirtualRootService
    ) {
        this.eventService = eventService;
        this.objectAccessService = objectAccessService;
        this.objectManager = objectManager;
        this.tenantScopeService = tenantScopeService;
        this.tenantVirtualRootService = tenantVirtualRootService;
    }

    @GetMapping
    public List<ObjectEvent> list(
            @RequestParam(required = false) String objectPath,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String filterPath,
            Authentication authentication
    ) {
        String canonicalObject = canonicalizeOptional(objectPath, authentication);
        String canonicalFilter = canonicalizeOptional(filterPath, authentication);
        if (canonicalObject != null) {
            tenantScopeService.requirePathInScope(canonicalObject, authentication);
        }
        if (canonicalFilter != null) {
            tenantScopeService.requirePathInScope(canonicalFilter, authentication);
        }
        return eventService.list(canonicalObject, limit, canonicalFilter).stream()
                .filter(event -> tenantScopeService.isPathVisible(event.objectPath(), authentication))
                .map(event -> virtualizeEvent(event, authentication))
                .toList();
    }

    @GetMapping("/journal-status")
    public Map<String, Object> journalStatus(
            @RequestParam(required = false) String objectPath,
            Authentication authentication
    ) {
        String canonical = canonicalizeOptional(objectPath, authentication);
        if (canonical != null) {
            tenantScopeService.requirePathInScope(canonical, authentication);
        }
        return eventService.journalStatus(canonical);
    }

    @PostMapping("/fire")
    public ObjectEvent fire(
            @RequestParam String objectPath,
            @RequestParam String eventName,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) Instant occurredAt,
            @RequestBody(required = false) DataRecordPayloadRequest payload,
            Authentication authentication
    ) {
        String canonical = tenantVirtualRootService.toCanonical(objectPath, authentication);
        tenantScopeService.requirePathInScope(canonical, authentication);
        PlatformObject node = objectManager.require(canonical);
        EventDescriptor descriptor = node.events().get(eventName);
        if (descriptor == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event: " + eventName);
        }
        objectAccessService.requireMemberInvoke(
                canonical,
                "event",
                eventName,
                descriptor.invokeRoles(),
                authentication
        );
        return virtualizeEvent(
                eventService.fire(canonical, eventName, payload, appId, occurredAt),
                authentication
        );
    }

    private String canonicalizeOptional(String path, Authentication authentication) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return tenantVirtualRootService.toCanonical(path, authentication);
    }

    private ObjectEvent virtualizeEvent(ObjectEvent event, Authentication authentication) {
        String virtual = tenantVirtualRootService.toVirtual(event.objectPath(), authentication);
        if (virtual == null || virtual.equals(event.objectPath())) {
            return event;
        }
        return new ObjectEvent(
                event.id(),
                Objects.requireNonNullElse(virtual, event.objectPath()),
                event.eventName(),
                event.level(),
                event.payload(),
                event.timestamp()
        );
    }
}
