package com.ispf.server.api;

import com.ispf.server.security.PlatformCredentialService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/credentials")
public class PlatformCredentialController {

    private final PlatformCredentialService credentialService;

    public PlatformCredentialController(PlatformCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping("/by-path")
    public Map<String, Object> describe(@RequestParam String path) {
        return credentialService.describe(path);
    }

    @PutMapping("/by-path")
    public Map<String, Object> upsert(@RequestParam String path, @RequestBody UpsertCredentialRequest request) {
        return credentialService.upsert(path, request.kind(), request.secret(), request.metadata());
    }

    public record UpsertCredentialRequest(String kind, String secret, Map<String, Object> metadata) {
    }
}
