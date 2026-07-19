package com.ispf.server.api;

import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.ObjectEvent;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.event.EventService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
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

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;
    private final ObjectAccessService objectAccessService;
    private final ObjectManager objectManager;

    public EventController(
            EventService eventService,
            ObjectAccessService objectAccessService,
            ObjectManager objectManager
    ) {
        this.eventService = eventService;
        this.objectAccessService = objectAccessService;
        this.objectManager = objectManager;
    }

    @GetMapping
    public List<ObjectEvent> list(
            @RequestParam(required = false) String objectPath,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String filterPath
    ) {
        return eventService.list(objectPath, limit, filterPath);
    }

    @GetMapping("/journal-status")
    public Map<String, Object> journalStatus(
            @RequestParam(required = false) String objectPath
    ) {
        return eventService.journalStatus(objectPath);
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
        PlatformObject node = objectManager.require(objectPath);
        EventDescriptor descriptor = node.events().get(eventName);
        if (descriptor == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event: " + eventName);
        }
        objectAccessService.requireMemberInvoke(
                objectPath,
                "event",
                eventName,
                descriptor.invokeRoles(),
                authentication
        );
        return eventService.fire(objectPath, eventName, payload, appId, occurredAt);
    }
}
