package com.ispf.server.api;

import com.ispf.server.platform.BrickExportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/brick")
public class BrickExportController {

    private final BrickExportService brickExportService;

    public BrickExportController(BrickExportService brickExportService) {
        this.brickExportService = brickExportService;
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
