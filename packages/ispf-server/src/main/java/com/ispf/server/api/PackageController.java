package com.ispf.server.api;

import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Phase 14: Application = bundle package import only.
 */
@RestController
@RequestMapping("/api/v1/platform/packages")
public class PackageController {

    private final ApplicationBundleDeployService bundleDeployService;

    public PackageController(ApplicationBundleDeployService bundleDeployService) {
        this.bundleDeployService = bundleDeployService;
    }

    @PostMapping("/import")
    public Map<String, Object> importPackage(
            @RequestParam(defaultValue = "default") String packageId,
            @Valid @RequestBody ApplicationBundleDeployService.BundleManifest manifest
    ) {
        return bundleDeployService.deploy(packageId, manifest);
    }
}
