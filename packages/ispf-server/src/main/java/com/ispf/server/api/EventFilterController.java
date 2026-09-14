package com.ispf.server.api;

import com.ispf.core.object.ObjectEvent;
import com.ispf.server.event.EventService;
import com.ispf.server.eventfilter.EventFilterObjectService;
import com.ispf.server.eventfilter.EventFilterObjectService.EventFilterDefinition;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/event-filters")
public class EventFilterController {

    private final EventFilterObjectService eventFilterObjectService;
    private final EventService eventService;
    private final TenantScopeService tenantScopeService;
    private final TenantVirtualRootService tenantVirtualRootService;
    private final ObjectAccessService objectAccessService;

    public EventFilterController(
            EventFilterObjectService eventFilterObjectService,
            EventService eventService,
            TenantScopeService tenantScopeService,
            TenantVirtualRootService tenantVirtualRootService,
            ObjectAccessService objectAccessService
    ) {
        this.eventFilterObjectService = eventFilterObjectService;
        this.eventService = eventService;
        this.tenantScopeService = tenantScopeService;
        this.tenantVirtualRootService = tenantVirtualRootService;
        this.objectAccessService = objectAccessService;
    }

    /** Apply a saved filter to the event journal (BL-174). */
    @GetMapping("/by-path/events")
    public List<ObjectEvent> apply(
            @RequestParam String path,
            @RequestParam(required = false) String objectPath,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        String canonical = requirePathRead(path, authentication);
        eventFilterObjectService.getByPath(canonical);
        if (objectPath != null && !objectPath.isBlank()) {
            tenantScopeService.requirePathInScope(
                    tenantVirtualRootService.toCanonical(objectPath, authentication),
                    authentication
            );
        }
        return eventService.list(objectPath, limit, canonical);
    }

    @GetMapping
    public List<EventFilterDefinition> list(Authentication authentication) {
        return eventFilterObjectService.list().stream()
                .filter(filter -> canReadPath(filter.path(), authentication))
                .toList();
    }

    @GetMapping("/by-path")
    public EventFilterDefinition get(@RequestParam String path, Authentication authentication) {
        return eventFilterObjectService.getByPath(requirePathRead(path, authentication));
    }

    @PostMapping
    public EventFilterDefinition create(
            @RequestBody SaveEventFilterRequest request,
            Authentication authentication
    ) {
        if (request.filterId() == null || request.filterId().isBlank()) {
            throw new IllegalArgumentException("filterId is required");
        }
        requirePathWrite(EventFilterObjectService.EVENT_FILTERS_ROOT, authentication);
        return eventFilterObjectService.upsert(toDefinition("", request));
    }

    @PutMapping("/by-path")
    public EventFilterDefinition update(
            @RequestParam String path,
            @RequestBody SaveEventFilterRequest request,
            Authentication authentication
    ) {
        String canonical = requirePathWrite(path, authentication);
        EventFilterDefinition existing = eventFilterObjectService.getByPath(canonical);
        EventFilterDefinition merged = new EventFilterDefinition(
                canonical,
                request.filterId() != null && !request.filterId().isBlank()
                        ? request.filterId()
                        : existing.filterId(),
                request.displayName() != null ? request.displayName() : existing.displayName(),
                request.description() != null ? request.description() : existing.description(),
                request.eventNamePattern() != null ? request.eventNamePattern() : existing.eventNamePattern(),
                request.sourceObjectPathPattern() != null
                        ? request.sourceObjectPathPattern()
                        : existing.sourceObjectPathPattern(),
                request.minSeverity() != null ? request.minSeverity() : existing.minSeverity(),
                request.maxSeverity() != null ? request.maxSeverity() : existing.maxSeverity(),
                request.timeWindowMs() != null ? request.timeWindowMs() : existing.timeWindowMs(),
                request.filterExpression() != null ? request.filterExpression() : existing.filterExpression(),
                request.enabled() != null ? request.enabled() : existing.enabled()
        );
        return eventFilterObjectService.upsert(merged);
    }

    @DeleteMapping("/by-path")
    public void delete(@RequestParam String path, Authentication authentication) {
        eventFilterObjectService.delete(requirePathWrite(path, authentication));
    }

    private boolean canReadPath(String path, Authentication authentication) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String canonical = tenantVirtualRootService.toCanonical(path, authentication);
        return tenantScopeService.isPathVisible(canonical, authentication)
                && objectAccessService.canRead(canonical, authentication);
    }

    private String requirePathRead(String path, Authentication authentication) {
        String canonical = requirePathAccess(path, authentication);
        objectAccessService.requireRead(canonical, authentication);
        return canonical;
    }

    private String requirePathWrite(String path, Authentication authentication) {
        String canonical = requirePathAccess(path, authentication);
        objectAccessService.requireWrite(canonical, authentication);
        return canonical;
    }

    private String requirePathAccess(String path, Authentication authentication) {
        String canonical = tenantVirtualRootService.toCanonical(path, authentication);
        tenantScopeService.requirePathInScope(canonical, authentication);
        return canonical;
    }

    private static EventFilterDefinition toDefinition(String path, SaveEventFilterRequest request) {
        return new EventFilterDefinition(
                path,
                request.filterId(),
                request.displayName(),
                request.description(),
                request.eventNamePattern() != null ? request.eventNamePattern() : "*",
                request.sourceObjectPathPattern() != null ? request.sourceObjectPathPattern() : "root.platform.**",
                request.minSeverity() != null ? request.minSeverity() : 0L,
                request.maxSeverity() != null ? request.maxSeverity() : 100L,
                request.timeWindowMs() != null ? request.timeWindowMs() : 0L,
                request.filterExpression() != null ? request.filterExpression() : "",
                request.enabled() == null || request.enabled()
        );
    }

    public record SaveEventFilterRequest(
            String filterId,
            String displayName,
            String description,
            String eventNamePattern,
            String sourceObjectPathPattern,
            Long minSeverity,
            Long maxSeverity,
            Long timeWindowMs,
            String filterExpression,
            Boolean enabled
    ) {
    }
}
