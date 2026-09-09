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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
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

    @GetMapping("/catalog/artifacts")
    public List<com.ispf.driver.DriverPointCatalog.ArtifactInfo> listCatalogArtifacts(
            @RequestParam(defaultValue = "snmp") String driverId,
            Authentication authentication
    ) {
        requireAuthenticated(authentication);
        return driverRuntimeService.listDriverArtifacts(driverId);
    }

    @PostMapping("/catalog/artifacts")
    public com.ispf.driver.DriverPointCatalog.ArtifactInfo importCatalogArtifact(
            @RequestParam(defaultValue = "snmp") String driverId,
            @RequestBody CatalogArtifactUploadRequest request,
            Authentication authentication
    ) {
        requireAuthenticated(authentication);
        if (request == null || request.fileName() == null || request.fileName().isBlank()) {
            throw new IllegalArgumentException("fileName is required");
        }
        byte[] content = decodeArtifactContent(request);
        if (content.length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Artifact content exceeds 2 MiB");
        }
        return driverRuntimeService.importDriverArtifact(driverId, request.fileName(), content);
    }

    @DeleteMapping("/catalog/artifacts")
    public Map<String, Object> deleteCatalogArtifact(
            @RequestParam(defaultValue = "snmp") String driverId,
            @RequestParam String name,
            Authentication authentication
    ) {
        requireAuthenticated(authentication);
        driverRuntimeService.deleteDriverArtifact(driverId, name);
        return Map.of("deleted", true, "name", name);
    }

    @GetMapping("/catalog/browse")
    public List<com.ispf.driver.DriverPointCatalog.CatalogNode> browseCatalog(
            @RequestParam(defaultValue = "snmp") String driverId,
            @RequestParam(required = false) String nodeId,
            Authentication authentication
    ) {
        requireAuthenticated(authentication);
        return driverRuntimeService.browseDriverCatalog(driverId, nodeId);
    }

    @PostMapping("/catalog/propose")
    public List<com.ispf.driver.DriverPointCatalog.PointProposal> proposeCatalogPoints(
            @RequestParam(defaultValue = "snmp") String driverId,
            @RequestBody ProposePointsRequest request,
            Authentication authentication
    ) {
        requireAuthenticated(authentication);
        List<com.ispf.driver.DriverPointCatalog.PointSelection> selections =
                request != null && request.selections() != null ? request.selections() : List.of();
        return driverRuntimeService.proposeDriverPoints(driverId, selections);
    }

    @PostMapping("/catalog/import-points")
    public DriverRuntimeService.ImportPointsResult importCatalogPoints(
            @RequestParam String devicePath,
            @RequestParam(defaultValue = "snmp") String driverId,
            @RequestBody ImportPointsRequest request,
            Authentication authentication
    ) {
        String canonical = requirePathAccess(devicePath, authentication);
        List<com.ispf.driver.DriverPointCatalog.PointProposal> proposals;
        if (request != null && request.proposals() != null && !request.proposals().isEmpty()) {
            proposals = request.proposals();
        } else {
            List<com.ispf.driver.DriverPointCatalog.PointSelection> selections =
                    request != null && request.selections() != null ? request.selections() : List.of();
            proposals = driverRuntimeService.proposeDriverPoints(driverId, selections);
        }
        return driverRuntimeService.importDriverPoints(canonical, proposals);
    }

    public record CatalogArtifactUploadRequest(String fileName, String contentBase64, String contentText) {
    }

    public record ProposePointsRequest(List<com.ispf.driver.DriverPointCatalog.PointSelection> selections) {
    }

    public record ImportPointsRequest(
            List<com.ispf.driver.DriverPointCatalog.PointSelection> selections,
            List<com.ispf.driver.DriverPointCatalog.PointProposal> proposals
    ) {
    }

    private static byte[] decodeArtifactContent(CatalogArtifactUploadRequest request) {
        if (request.contentText() != null && !request.contentText().isBlank()) {
            return request.contentText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (request.contentBase64() == null || request.contentBase64().isBlank()) {
            throw new IllegalArgumentException("contentText or contentBase64 is required");
        }
        try {
            return Base64.getDecoder().decode(request.contentBase64().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid contentBase64", e);
        }
    }

    private static void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication required");
        }
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
