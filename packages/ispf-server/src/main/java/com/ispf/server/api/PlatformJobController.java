package com.ispf.server.api;

import com.ispf.server.platform.PlatformJobService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/jobs")
public class PlatformJobController {

    private final PlatformJobService jobService;
    private final ObjectMapper objectMapper;

    public PlatformJobController(PlatformJobService jobService, ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{jobId}")
    public Map<String, Object> get(@PathVariable UUID jobId) {
        PlatformJobService.JobView job = jobService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
        return job.toApiMap(objectMapper);
    }
}
