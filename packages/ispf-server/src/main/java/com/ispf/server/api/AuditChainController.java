package com.ispf.server.api;

import com.ispf.server.audit.AuditChainStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditChainController {

    private final AuditChainStore auditChainStore;

    public AuditChainController(AuditChainStore auditChainStore) {
        this.auditChainStore = auditChainStore;
    }

    @GetMapping("/verify-chain")
    public Map<String, Object> verifyChain() {
        boolean intact = auditChainStore.verify();
        return Map.of("intact", intact);
    }
}
