package com.ispf.server.api;

import com.ispf.core.object.ObjectEvent;
import com.ispf.core.model.DataRecord;
import com.ispf.server.event.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<ObjectEvent> list(
            @RequestParam(required = false) String objectPath,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return eventService.list(objectPath, limit);
    }

    @PostMapping("/fire")
    public ObjectEvent fire(
            @RequestParam String objectPath,
            @RequestParam String eventName,
            @RequestBody(required = false) DataRecord payload
    ) {
        return eventService.fire(objectPath, eventName, payload);
    }
}
