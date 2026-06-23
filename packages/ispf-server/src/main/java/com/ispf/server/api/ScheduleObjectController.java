package com.ispf.server.api;

import com.ispf.server.schedule.ScheduleObjectService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-schedules")
public class ScheduleObjectController {

    private final ScheduleObjectService scheduleObjectService;

    public ScheduleObjectController(ScheduleObjectService scheduleObjectService) {
        this.scheduleObjectService = scheduleObjectService;
    }

    @GetMapping("/by-path")
    public ScheduleObjectService.ScheduleView get(@RequestParam String path) {
        return scheduleObjectService.getByPath(path);
    }

    @PostMapping
    public ScheduleObjectService.ScheduleView create(@RequestBody CreateScheduleRequest request) {
        return scheduleObjectService.create(
                request.scheduleId(),
                request.displayName(),
                request.description(),
                request.enabled(),
                request.intervalMs(),
                request.objectPath(),
                request.functionName()
        );
    }

    @PutMapping("/by-path")
    public ScheduleObjectService.ScheduleView update(
            @RequestParam String path,
            @RequestBody UpdateScheduleRequest request
    ) {
        return scheduleObjectService.update(
                path,
                request.displayName(),
                request.description(),
                request.enabled(),
                request.intervalMs(),
                request.objectPath(),
                request.functionName()
        );
    }

    public record CreateScheduleRequest(
            @NotBlank String scheduleId,
            String displayName,
            String description,
            Boolean enabled,
            Long intervalMs,
            String objectPath,
            String functionName
    ) {
    }

    public record UpdateScheduleRequest(
            String displayName,
            String description,
            Boolean enabled,
            Long intervalMs,
            String objectPath,
            String functionName
    ) {
    }
}
