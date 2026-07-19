package com.ispf.server.api;

import com.ispf.core.object.ObjectEvent;
import com.ispf.server.event.EventService;
import com.ispf.server.eventfilter.EventFilterObjectService;
import com.ispf.server.eventfilter.EventFilterObjectService.EventFilterDefinition;
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

    public EventFilterController(EventFilterObjectService eventFilterObjectService, EventService eventService) {
        this.eventFilterObjectService = eventFilterObjectService;
        this.eventService = eventService;
    }

    /** Apply a saved filter to the event journal (BL-174). */
    @GetMapping("/by-path/events")
    public List<ObjectEvent> apply(
            @RequestParam String path,
            @RequestParam(required = false) String objectPath,
            @RequestParam(defaultValue = "50") int limit
    ) {
        eventFilterObjectService.getByPath(path);
        return eventService.list(objectPath, limit, path);
    }

    @GetMapping
    public List<EventFilterDefinition> list() {
        return eventFilterObjectService.list();
    }

    @GetMapping("/by-path")
    public EventFilterDefinition get(@RequestParam String path) {
        return eventFilterObjectService.getByPath(path);
    }

    @PostMapping
    public EventFilterDefinition create(@RequestBody SaveEventFilterRequest request) {
        if (request.filterId() == null || request.filterId().isBlank()) {
            throw new IllegalArgumentException("filterId is required");
        }
        return eventFilterObjectService.upsert(toDefinition("", request));
    }

    @PutMapping("/by-path")
    public EventFilterDefinition update(
            @RequestParam String path,
            @RequestBody SaveEventFilterRequest request
    ) {
        EventFilterDefinition existing = eventFilterObjectService.getByPath(path);
        EventFilterDefinition merged = new EventFilterDefinition(
                path,
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
    public void delete(@RequestParam String path) {
        eventFilterObjectService.delete(path);
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
