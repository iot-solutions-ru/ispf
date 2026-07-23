package com.ispf.server.api;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.api.dto.DataRecordPayloadResolver;
import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.driver.TelemetryPublishMode;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/drivers/runtime")
public class DriverRuntimeController {

    private final DriverRuntimeService driverRuntimeService;
    private final SystemObjectStructureService structureService;
    private final TenantScopeService tenantScopeService;
    private final TenantVirtualRootService tenantVirtualRootService;

    public DriverRuntimeController(
            DriverRuntimeService driverRuntimeService,
            SystemObjectStructureService structureService,
            TenantScopeService tenantScopeService,
            TenantVirtualRootService tenantVirtualRootService
    ) {
        this.driverRuntimeService = driverRuntimeService;
        this.structureService = structureService;
        this.tenantScopeService = tenantScopeService;
        this.tenantVirtualRootService = tenantVirtualRootService;
    }

    @GetMapping("/status")
    public DriverRuntimeService.DriverRuntimeStatus status(
            @RequestParam String devicePath,
            Authentication authentication
    ) {
        String canonical = requirePathAccess(devicePath, authentication);
        return driverRuntimeService.status(canonical)
                .orElseThrow(() -> new IllegalArgumentException("No driver binding for: " + devicePath));
    }

    @PostMapping("/start")
    public DriverRuntimeService.DriverRuntimeStatus start(
            @RequestParam String devicePath,
            Authentication authentication
    ) {
        return driverRuntimeService.start(requirePathAccess(devicePath, authentication));
    }

    @PostMapping("/stop")
    public DriverRuntimeService.DriverRuntimeStatus stop(
            @RequestParam String devicePath,
            Authentication authentication
    ) {
        return driverRuntimeService.stop(requirePathAccess(devicePath, authentication));
    }

    @PostMapping("/poll")
    public DriverRuntimeService.DriverRuntimeStatus poll(
            @RequestParam String devicePath,
            @RequestParam(required = false) String pointId,
            Authentication authentication
    ) {
        return driverRuntimeService.pollNow(requirePathAccess(devicePath, authentication), pointId);
    }

    @GetMapping("/browse")
    public java.util.List<com.ispf.driver.DriverDiscovery.Node> browse(
            @RequestParam String devicePath,
            @RequestParam(required = false) String nodeId,
            Authentication authentication
    ) {
        return driverRuntimeService.browseDriverChildren(requirePathAccess(devicePath, authentication), nodeId);
    }

    @PostMapping("/write")
    public DriverRuntimeService.DriverRuntimeStatus write(
            @RequestParam String devicePath,
            @RequestParam String pointId,
            @RequestBody(required = false) DataRecordPayloadRequest value,
            Authentication authentication
    ) {
        String canonical = requirePathAccess(devicePath, authentication);
        DataSchema schema = DataSchema.builder("driverWrite")
                .field("value", FieldType.STRING)
                .build();
        DataRecord record = DataRecordPayloadResolver.resolve(schema, value);
        return driverRuntimeService.writePoint(canonical, pointId, record);
    }

    @PutMapping("/configure")
    public DriverRuntimeService.DriverRuntimeStatus configure(
            @RequestParam String devicePath,
            @RequestBody ConfigureDriverRequest request,
            Authentication authentication
    ) {
        String canonical = requirePathAccess(devicePath, authentication);
        structureService.ensureDeviceDriverStructure(canonical);
        Map<String, String> configuration = mergeConfiguration(request);
        TelemetryPublishMode publishMode = TelemetryPublishMode.parse(configuration.get("telemetryPublishMode"));
        int coalesceMs = parsePositiveInt(configuration.get("telemetryCoalesceMs"));
        DriverBinding binding = DriverBinding.of(
                request.driverId() != null ? request.driverId() : DriverBinding.DEFAULT_DRIVER_ID,
                request.pollIntervalMs() != null ? request.pollIntervalMs() : 2000,
                configuration,
                request.pointMappings() != null ? request.pointMappings() : Map.of(),
                publishMode,
                coalesceMs
        );
        driverRuntimeService.configure(canonical, binding);
        if (Boolean.TRUE.equals(request.autoStart())) {
            driverRuntimeService.setDriverAutoStart(canonical, true);
            return driverRuntimeService.start(canonical);
        }
        return driverRuntimeService.status(canonical).orElseThrow();
    }

    private String requirePathAccess(String path, Authentication authentication) {
        String canonical = tenantVirtualRootService.toCanonical(path, authentication);
        tenantScopeService.requirePathInScope(canonical, authentication);
        return canonical;
    }

    public record ConfigureDriverRequest(
            String driverId,
            Integer pollIntervalMs,
            Map<String, String> configuration,
            Map<String, String> pointMappings,
            String telemetryPublishMode,
            Integer telemetryCoalesceMs,
            Boolean autoStart
    ) {
    }

    private static Map<String, String> mergeConfiguration(ConfigureDriverRequest request) {
        Map<String, String> configuration = new java.util.LinkedHashMap<>(
                request.configuration() != null ? request.configuration() : Map.of()
        );
        if (request.telemetryPublishMode() != null && !request.telemetryPublishMode().isBlank()) {
            configuration.put("telemetryPublishMode", request.telemetryPublishMode().trim());
        }
        if (request.telemetryCoalesceMs() != null && request.telemetryCoalesceMs() > 0) {
            configuration.put("telemetryCoalesceMs", request.telemetryCoalesceMs().toString());
        }
        return Map.copyOf(configuration);
    }

    private static int parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
