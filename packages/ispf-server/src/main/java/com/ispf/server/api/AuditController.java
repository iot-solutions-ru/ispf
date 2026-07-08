package com.ispf.server.api;

import com.ispf.server.audit.AuditEventService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditController {

    private final AuditEventService auditEventService;

    public AuditController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @GetMapping
    public List<AuditEventService.AuditEvent> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        return auditEventService.listRecent(category, limit);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "1000") int limit
    ) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        auditEventService.streamCsv(category, limit, buffer);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-events.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(buffer.toByteArray());
    }
}
