package com.ispf.server.api;

import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.BundleDependencyException;
import com.ispf.server.license.CommercialLicenseException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
        try {
            return bundleDeployService.deploy(packageId, manifest);
        } catch (CommercialLicenseException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        } catch (BundleDependencyException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
