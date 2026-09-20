package com.ispf.server.api;

import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.security.acl.ObjectAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Admin-only edit leases on object-tree path prefixes ({@code /api/v1/objects/leases}).
 * Split out of {@code ObjectController}; URL contract unchanged.
 */
@RestController
@RequestMapping("/api/v1/objects/leases")
public class ObjectEditLeaseController {

    private static final Duration DEFAULT_TTL = Duration.ofHours(2);

    private final ObjectAccessService objectAccessService;
    private final ObjectEditLeaseService editLeaseService;

    public ObjectEditLeaseController(
            ObjectAccessService objectAccessService,
            ObjectEditLeaseService editLeaseService
    ) {
        this.objectAccessService = objectAccessService;
        this.editLeaseService = editLeaseService;
    }

    @GetMapping
    public List<ObjectEditLeaseService.EditLease> listLeases(Authentication authentication) {
        objectAccessService.requireAdmin(authentication);
        return editLeaseService.listActive();
    }

    @PostMapping
    public ObjectEditLeaseService.EditLease acquireLease(
            @Valid @RequestBody AcquireLeaseRequest request,
            Authentication authentication
    ) {
        objectAccessService.requireAdmin(authentication);
        Duration ttl = request.ttlMinutes() != null
                ? Duration.ofMinutes(request.ttlMinutes())
                : DEFAULT_TTL;
        return editLeaseService.acquire(request.pathPrefix(), authentication.getName(), ttl);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseLease(@RequestParam String pathPrefix, Authentication authentication) {
        objectAccessService.requireAdmin(authentication);
        editLeaseService.release(pathPrefix, authentication.getName());
    }

    public record AcquireLeaseRequest(
            @NotBlank String pathPrefix,
            Integer ttlMinutes
    ) {
    }
}
