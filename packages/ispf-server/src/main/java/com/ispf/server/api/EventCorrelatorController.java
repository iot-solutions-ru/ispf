package com.ispf.server.api;

import com.ispf.server.correlator.EventCorrelator;
import com.ispf.server.correlator.EventCorrelatorService;
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

    @GetMapping("/by-path")
    public EventCorrelator get(@RequestParam String path) {
        return correlatorService.get(path);
    }

    @PostMapping
    public EventCorrelator create(@RequestBody EventCorrelatorService.CreateCorrelatorRequest request) {
        return correlatorService.create(request);
    }

    @PutMapping("/by-path")
    public EventCorrelator update(
            @RequestParam String path,
            @RequestBody EventCorrelatorService.UpdateCorrelatorRequest request
    ) {
        return correlatorService.update(path, request);
    }

    @DeleteMapping("/by-path")
    public void delete(@RequestParam String path) {
        correlatorService.delete(path);
    }
}
