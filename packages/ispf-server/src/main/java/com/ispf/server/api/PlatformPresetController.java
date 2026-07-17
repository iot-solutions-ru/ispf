package com.ispf.server.api;

import com.ispf.server.bootstrap.VirtClusterPresetService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/presets")
public class PlatformPresetController {

    private final VirtClusterPresetService virtClusterPresetService;

    public PlatformPresetController(VirtClusterPresetService virtClusterPresetService) {
        this.virtClusterPresetService = virtClusterPresetService;
    }

    @PostMapping("/virt-cluster")
    public Map<String, Object> installVirtCluster(@RequestBody(required = false) VirtClusterRequest request) {
        try {
            VirtClusterRequest body = request != null ? request : new VirtClusterRequest(true, "platform");
            boolean wire = body.wireOperatorApp() == null || body.wireOperatorApp();
            String appId = body.operatorAppId() != null ? body.operatorAppId() : "platform";
            return virtClusterPresetService.install(wire, appId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    public record VirtClusterRequest(Boolean wireOperatorApp, String operatorAppId) {
    }
}
