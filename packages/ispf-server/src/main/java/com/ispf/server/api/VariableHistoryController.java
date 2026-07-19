package com.ispf.server.api;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.federation.FederationProxyService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.time.PlatformCalendarRangeService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/objects/by-path/variables")
public class VariableHistoryController {

    private final VariableHistoryService variableHistoryService;
    private final FederationProxyService federationProxyService;
    private final PlatformCalendarRangeService calendarRangeService;
    private final ObjectMapper objectMapper;
    private final ObjectManager objectManager;
    private final ObjectAccessService objectAccessService;
    private final TenantScopeService tenantScopeService;

    public VariableHistoryController(
            VariableHistoryService variableHistoryService,
            FederationProxyService federationProxyService,
            PlatformCalendarRangeService calendarRangeService,
            ObjectMapper objectMapper,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        this.variableHistoryService = variableHistoryService;
        this.federationProxyService = federationProxyService;
        this.calendarRangeService = calendarRangeService;
        this.objectMapper = objectMapper;
        this.objectManager = objectManager;
        this.objectAccessService = objectAccessService;
        this.tenantScopeService = tenantScopeService;
    }

    @GetMapping("/history")
    public VariableHistoryService.VariableHistoryResponse history(
            @RequestParam String path,
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "value") String field,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String calendarRange,
            @RequestParam(required = false) String timeZone,
            @RequestParam(required = false, defaultValue = "500") int limit,
            Authentication authentication
    ) {
        requireVariableHistoryRead(path, name, authentication);
        try {
            InstantRange range = resolveRange(from, to, calendarRange, timeZone);
            return federationProxyService.resolve(path)
                    .map(target -> objectMapper.convertValue(
                            federationProxyService.proxyVariableHistory(
                                    target, name, field, range.from(), range.to(), limit
                            ),
                            VariableHistoryService.VariableHistoryResponse.class
                    ))
                    .orElseGet(() -> variableHistoryService.query(
                            path, name, field, range.from(), range.to(), limit
                    ));
        } catch (IllegalArgumentException e) {
            throw mapIllegalArgument(e);
        }
    }

    @GetMapping("/history/aggregate")
    public VariableHistoryService.VariableHistoryAggregateResponse aggregateHistory(
            @RequestParam String path,
            @RequestParam String name,
            @RequestParam String bucket,
            @RequestParam(required = false, defaultValue = "value") String field,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String calendarRange,
            @RequestParam(required = false) String timeZone,
            @RequestParam(required = false, defaultValue = "500") int limit,
            Authentication authentication
    ) {
        requireVariableHistoryRead(path, name, authentication);
        try {
            InstantRange range = resolveRange(from, to, calendarRange, timeZone);
            return federationProxyService.resolve(path)
                    .map(target -> objectMapper.convertValue(
                            federationProxyService.proxyVariableHistoryAggregate(
                                    target, name, field, bucket, range.from(), range.to(), limit
                            ),
                            VariableHistoryService.VariableHistoryAggregateResponse.class
                    ))
                    .orElseGet(() -> variableHistoryService.aggregate(
                            path, name, field, range.from(), range.to(), bucket, limit
                    ));
        } catch (IllegalArgumentException e) {
            throw mapIllegalArgument(e);
        }
    }

    @GetMapping("/history/export")
    public ResponseEntity<?> exportHistory(
            @RequestParam String path,
            @RequestParam String name,
            @RequestParam String format,
            @RequestParam(required = false, defaultValue = "value") String field,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String calendarRange,
            @RequestParam(required = false) String timeZone,
            @RequestParam(required = false, defaultValue = "10000") int limit,
            Authentication authentication
    ) throws IOException {
        requireVariableHistoryRead(path, name, authentication);
        String normalizedFormat = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        try {
            InstantRange range = resolveRange(from, to, calendarRange, timeZone);
            return switch (normalizedFormat) {
                case "csv" -> {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    variableHistoryService.streamCsv(
                            path, name, field, range.from(), range.to(), limit, buffer
                    );
                    yield ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(name, field, "csv"))
                            .contentType(new MediaType("text", "csv"))
                            .body(buffer.toByteArray());
                }
                case "json" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(name, field, "json"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(variableHistoryService.exportJson(
                                path, name, field, range.from(), range.to(), limit
                        ));
                case "parquet" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(name, field, "parquet"))
                        .header("X-ISPF-Export-Format", "parquet")
                        .contentType(new MediaType("application", "vnd.apache.parquet"))
                        .body(variableHistoryService.exportParquet(
                                path, name, field, range.from(), range.to(), limit
                        ));
                default -> throw new IllegalArgumentException("Unsupported export format: " + format);
            };
        } catch (IllegalArgumentException e) {
            throw mapIllegalArgument(e);
        }
    }

    private void requireVariableHistoryRead(String path, String name, Authentication authentication) {
        tenantScopeService.requirePathInScope(path, authentication);
        PlatformObject node = objectManager.require(path);
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable: " + name));
        objectAccessService.requireVariableRead(path, name, variable.readRoles(), authentication);
    }

    private InstantRange resolveRange(
            Instant from,
            Instant to,
            String calendarRange,
            String timeZone
    ) {
        if (calendarRange != null && !calendarRange.isBlank()) {
            PlatformCalendarRangeService.InstantRange resolved =
                    calendarRangeService.resolve(calendarRange, timeZone);
            return new InstantRange(resolved.from(), resolved.to());
        }
        return new InstantRange(from, to);
    }

    private static ResponseStatusException mapIllegalArgument(IllegalArgumentException e) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        if (e.getMessage() != null) {
            if (e.getMessage().startsWith("Unsupported bucket")
                    || e.getMessage().startsWith("Unsupported calendarRange")
                    || e.getMessage().startsWith("Unsupported export format")) {
                status = HttpStatus.BAD_REQUEST;
            } else if (e.getMessage().startsWith("Invalid IANA timeZone")) {
                status = HttpStatus.BAD_REQUEST;
            }
        }
        return new ResponseStatusException(status, e.getMessage());
    }

    private record InstantRange(Instant from, Instant to) {
    }

    private static String attachmentHeader(String variableName, String field, String format) {
        return "attachment; filename=\"" + VariableHistoryService.exportFileName(variableName, field, format) + "\"";
    }
}
