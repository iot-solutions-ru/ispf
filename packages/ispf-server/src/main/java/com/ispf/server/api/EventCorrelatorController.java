package com.ispf.server.api;

import com.ispf.server.correlator.EventCorrelator;
import com.ispf.server.correlator.EventCorrelatorService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/correlators")
public class EventCorrelatorController {

    private final EventCorrelatorService correlatorService;

    public EventCorrelatorController(EventCorrelatorService correlatorService) {
        this.correlatorService = correlatorService;
    }

    @GetMapping
    public List<EventCorrelator> list() {
        return correlatorService.list();
    }

    @GetMapping("/{id}")
    public EventCorrelator get(@PathVariable String id) {
        return correlatorService.get(id);
    }

    @PostMapping
    public EventCorrelator create(@RequestBody EventCorrelatorService.CreateCorrelatorRequest request) {
        return correlatorService.create(request);
    }

    @PutMapping("/{id}")
    public EventCorrelator update(
            @PathVariable String id,
            @RequestBody EventCorrelatorService.UpdateCorrelatorRequest request
    ) {
        return correlatorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        correlatorService.delete(id);
    }
}
