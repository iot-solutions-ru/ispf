package com.ispf.server.api;

import com.ispf.server.audit.AuditEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
