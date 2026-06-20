package com.ispf.server.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.federation.FederationProxyService;
import com.ispf.server.history.VariableHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/objects/by-path/variables")
public class VariableHistoryController {

    private final VariableHistoryService variableHistoryService;
    private final FederationProxyService federationProxyService;
    private final ObjectMapper objectMapper;

    public VariableHistoryController(
            VariableHistoryService variableHistoryService,
            FederationProxyService federationProxyService,
            ObjectMapper objectMapper
    ) {
        this.variableHistoryService = variableHistoryService;
        this.federationProxyService = federationProxyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/history")
    public VariableHistoryService.VariableHistoryResponse history(
            @RequestParam String path,
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "value") String field,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "500") int limit
    ) {
        try {
            return federationProxyService.resolve(path)
                    .map(target -> objectMapper.convertValue(
                            federationProxyService.proxyVariableHistory(target, name, field, from, to, limit),
                            VariableHistoryService.VariableHistoryResponse.class
                    ))
                    .orElseGet(() -> variableHistoryService.query(path, name, field, from, to, limit));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
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
            @RequestParam(required = false, defaultValue = "500") int limit
    ) {
        try {
            return federationProxyService.resolve(path)
                    .map(target -> objectMapper.convertValue(
                            federationProxyService.proxyVariableHistoryAggregate(
                                    target, name, field, bucket, from, to, limit
                            ),
                            VariableHistoryService.VariableHistoryAggregateResponse.class
                    ))
                    .orElseGet(() -> variableHistoryService.aggregate(path, name, field, from, to, bucket, limit));
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Unsupported bucket")
                    ? HttpStatus.BAD_REQUEST
                    : HttpStatus.NOT_FOUND;
            throw new ResponseStatusException(status, e.getMessage());
        }
    }

    @GetMapping("/history/export")
    public ResponseEntity<byte[]> exportHistory(
            @RequestParam String path,
            @RequestParam String name,
            @RequestParam String format,
            @RequestParam(required = false, defaultValue = "value") String field,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "10000") int limit
    ) {
        String normalizedFormat = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        try {
            return switch (normalizedFormat) {
                case "csv" -> {
                    String csv = variableHistoryService.exportCsv(path, name, field, from, to, limit);
                    yield ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(name, field, "csv"))
                            .contentType(new MediaType("text", "csv"))
                            .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                case "json" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(name, field, "json"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(variableHistoryService.exportJson(path, name, field, from, to, limit));
                default -> throw new IllegalArgumentException("Unsupported export format: " + format);
            };
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    "Unsupported export format: ".equals(e.getMessage()) || e.getMessage().startsWith("Unsupported")
                            ? HttpStatus.BAD_REQUEST
                            : HttpStatus.NOT_FOUND,
                    e.getMessage()
            );
        }
    }

    private static String attachmentHeader(String variableName, String field, String format) {
        return "attachment; filename=\"" + VariableHistoryService.exportFileName(variableName, field, format) + "\"";
    }
}
