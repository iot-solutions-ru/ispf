package com.ispf.server.api;

import com.ispf.server.platform.BrickClassInferenceService;
import com.ispf.server.platform.BrickExportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/brick")
public class BrickExportController {

    private final BrickExportService brickExportService;
    private final BrickClassInferenceService brickClassInferenceService;

    public BrickExportController(
            BrickExportService brickExportService,
            BrickClassInferenceService brickClassInferenceService
    ) {
        this.brickExportService = brickExportService;
        this.brickClassInferenceService = brickClassInferenceService;
    }

    @GetMapping("/infer")
    public Map<String, Object> infer(
            Authentication authentication,
            @RequestParam(required = false) String objectPath,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String haystackKind,
            @RequestParam(required = false) String displayName
    ) {
        boolean hasObjectPath = objectPath != null && !objectPath.isBlank();
        boolean hasTags = tags != null && !tags.isBlank();
        if (hasObjectPath == hasTags) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide exactly one of objectPath or tags."
            );
        }
        if (hasObjectPath) {
            return brickClassInferenceService.inferFromObjectPath(objectPath);
        }
        List<String> tagList = Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        return brickClassInferenceService.inferFromTags(tagList, haystackKind, displayName);
    }

    @GetMapping(value = "/export", produces = {MediaType.APPLICATION_JSON_VALUE, "text/turtle"})
    public ResponseEntity<?> export(
            Authentication authentication,
            @RequestParam(required = false) String rootPath,
            @RequestParam(defaultValue = "jsonld") String format,
            @RequestParam(defaultValue = "true") boolean includePoints
    ) {
        String normalizedFormat = BrickExportService.normalizeFormat(format);
        if ("turtle".equals(normalizedFormat)) {
            String turtle = brickExportService.exportTurtle(rootPath, includePoints);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/turtle"))
                    .body(turtle);
        }
        Map<String, Object> payload = brickExportService.exportJsonLd(rootPath, includePoints);
        return ResponseEntity.ok(payload);
    }
}
