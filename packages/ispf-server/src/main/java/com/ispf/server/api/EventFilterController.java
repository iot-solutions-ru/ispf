package com.ispf.server.api;

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

    public EventFilterController(EventFilterObjectService eventFilterObjectService) {
        this.eventFilterObjectService = eventFilterObjectService;
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
    public EventFilterDefinition create(@RequestBody EventFilterDefinition definition) {
        return eventFilterObjectService.upsert(definition);
    }

    @PutMapping("/by-path")
    public EventFilterDefinition update(
            @RequestParam String path,
            @RequestBody EventFilterDefinition definition
    ) {
        EventFilterDefinition existing = eventFilterObjectService.getByPath(path);
        EventFilterDefinition merged = new EventFilterDefinition(
                path,
                definition.filterId() != null && !definition.filterId().isBlank()
                        ? definition.filterId()
                        : existing.filterId(),
                definition.displayName() != null ? definition.displayName() : existing.displayName(),
                definition.description() != null ? definition.description() : existing.description(),
                definition.eventNamePattern() != null ? definition.eventNamePattern() : existing.eventNamePattern(),
                definition.sourceObjectPathPattern() != null
                        ? definition.sourceObjectPathPattern()
                        : existing.sourceObjectPathPattern(),
                definition.minSeverity(),
                definition.maxSeverity(),
                definition.timeWindowMs(),
                definition.filterExpression() != null ? definition.filterExpression() : existing.filterExpression(),
                definition.enabled()
        );
        return eventFilterObjectService.upsert(merged);
    }

    @DeleteMapping("/by-path")
    public void delete(@RequestParam String path) {
        eventFilterObjectService.delete(path);
    }
}
