package com.ispf.server.platform.analytics;

import com.ispf.server.history.HistorianQueryMetricsRecorder;
import com.ispf.server.config.VariableHistorySloProperties;
import com.ispf.server.config.AnalyticsSloProperties;
import com.ispf.server.platform.analytics.AnalyticsDerivedTagService;
import com.ispf.server.platform.analytics.AssetAnalyticsService;
import com.ispf.server.platform.analytics.AssetAnalyticsService.AnalyticsTemplate;
import com.ispf.server.platform.analytics.AssetAnalyticsService.ApplyTemplateCommand;
import com.ispf.server.platform.analytics.AssetAnalyticsService.ApplyTemplateResult;
import com.ispf.server.platform.analytics.engine.AnalyticsBackfillService;
import com.ispf.server.history.HistorianRollupMaterializerService;
import com.ispf.server.platform.analytics.frames.EventFrame;
import com.ispf.server.platform.analytics.frames.EventFrameMesShiftBridge;
import com.ispf.server.platform.analytics.frames.EventFrameService;
import com.ispf.server.platform.analytics.frames.EventFrameType;
import com.ispf.server.platform.analytics.catalog.AnalyticsCatalogEntry;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagCatalogEntry;
import com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@RestController
@RequestMapping("/api/v1/platform/analytics")
public class PlatformAnalyticsController {

    private final AssetAnalyticsService assetAnalyticsService;
    private final AnalyticsDerivedTagService derivedTagService;
    private final HistorianQueryMetricsRecorder historianQueryMetricsRecorder;
    private final VariableHistorySloProperties sloProperties;
    private final AnalyticsSloProperties analyticsSloProperties;
    private final AnalyticsBackfillService backfillService;
    private final HistorianRollupMaterializerService rollupMaterializerService;
    private final AnalyticsQueryService analyticsQueryService;
    private final AnalyticsQueryExportService analyticsQueryExportService;
    private final EventFrameService eventFrameService;
    private final AnalyticsTagCatalogService tagCatalogService;
    private final AnalyticsCatalogService analyticsCatalogService;
    private final AnalyticsExpressionService expressionService;

    public PlatformAnalyticsController(
            AssetAnalyticsService assetAnalyticsService,
            AnalyticsDerivedTagService derivedTagService,
            HistorianQueryMetricsRecorder historianQueryMetricsRecorder,
            VariableHistorySloProperties sloProperties,
            AnalyticsSloProperties analyticsSloProperties,
            AnalyticsBackfillService backfillService,
            HistorianRollupMaterializerService rollupMaterializerService,
            AnalyticsQueryService analyticsQueryService,
            AnalyticsQueryExportService analyticsQueryExportService,
            EventFrameService eventFrameService,
            AnalyticsTagCatalogService tagCatalogService,
            AnalyticsCatalogService analyticsCatalogService,
            AnalyticsExpressionService expressionService
    ) {
        this.assetAnalyticsService = assetAnalyticsService;
        this.derivedTagService = derivedTagService;
        this.historianQueryMetricsRecorder = historianQueryMetricsRecorder;
        this.sloProperties = sloProperties;
        this.analyticsSloProperties = analyticsSloProperties;
        this.backfillService = backfillService;
        this.rollupMaterializerService = rollupMaterializerService;
        this.analyticsQueryService = analyticsQueryService;
        this.analyticsQueryExportService = analyticsQueryExportService;
        this.eventFrameService = eventFrameService;
        this.tagCatalogService = tagCatalogService;
        this.analyticsCatalogService = analyticsCatalogService;
        this.expressionService = expressionService;
    }

    @GetMapping("/templates")
    public List<AnalyticsTemplate> templates() {
        return assetAnalyticsService.listTemplates();
    }

    @GetMapping("/templates/by-path")
    public AnalyticsTemplate templateByPath(@RequestParam String path) {
        return assetAnalyticsService.getByPath(path);
    }

    @PostMapping("/templates")
    public AnalyticsTemplate createTemplate(@RequestBody AnalyticsTemplate definition) {
        return assetAnalyticsService.createTemplate(definition);
    }

    @PutMapping("/templates/by-path")
    public AnalyticsTemplate updateTemplate(@RequestParam String path, @RequestBody AnalyticsTemplate definition) {
        return assetAnalyticsService.updateTemplate(path, definition);
    }

    @DeleteMapping("/templates/by-path")
    public void deleteTemplate(@RequestParam String path) {
        assetAnalyticsService.deleteTemplate(path);
    }

    @PostMapping("/templates/apply")
    public ApplyTemplateResult applyTemplate(@RequestBody ApplyTemplateCommand command) {
        return assetAnalyticsService.applyTemplateToDevice(command);
    }

    @PostMapping("/derived-tags/refresh")
    public AnalyticsDerivedTagService.DerivedTagRefreshResult refreshDerivedTag(@RequestParam String devicePath) {
        return derivedTagService.refreshDevice(devicePath);
    }

    @GetMapping("/derived-tags/devices")
    public List<String> listDerivedDevices() {
        return derivedTagService.listDerivedDevicePaths();
    }

    /** List deployed analytics tags with lineage and impact metadata (BL-209). */
    @GetMapping("/tags")
    public Map<String, Object> listTags(@RequestParam(required = false) String path) {
        List<AnalyticsTagCatalogEntry> tags = tagCatalogService.listCatalogEntries(path);
        return Map.of("count", tags.size(), "tags", tags);
    }

    /** Analytics tag catalog entry with lineage graph (BL-209). */
    @GetMapping("/tags/by-path")
    public AnalyticsTagCatalogEntry tagByPath(@RequestParam String path) {
        return tagCatalogService.getCatalogEntry(path);
    }

    /** Historian query SLA snapshot: p50/p95 latency vs documented SLO (BL-161). */
    @GetMapping("/historian-sla")
    public Map<String, Object> historianQuerySla() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(historianQueryMetricsRecorder.snapshot(sloProperties).toMap());
        payload.put("analyticsSlo", analyticsSloProperties.toMap());
        return payload;
    }

    /** Documented analytics platform SLO targets (BL-210). */
    @GetMapping("/analytics-slo")
    public Map<String, Object> analyticsSlo() {
        return analyticsSloProperties.toMap();
    }

    /** Recompute derived tag values for a historian window ending at {@code to} (BL-204). */
    @PostMapping("/tags/backfill")
    public AnalyticsBackfillService.BackfillResult backfillTag(
            @RequestParam String path,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return backfillService.backfill(path, from, to);
    }

    /** Rebuild materialized historian rollups for a subscription window (BL-205). */
    @PostMapping("/rollups/rebuild")
    public HistorianRollupMaterializerService.RebuildResult rebuildRollups(
            @RequestParam String path,
            @RequestParam String variable,
            @RequestParam(required = false, defaultValue = "value") String field,
            @RequestParam String bucket,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return rollupMaterializerService.rebuild(path, variable, field, bucket, from, to);
    }

    /** Multi-tag aligned historian aggregate query (BL-206). */
    @PostMapping("/query")
    public AnalyticsQueryResponse query(@RequestBody AnalyticsQueryRequest request) {
        try {
            return analyticsQueryService.query(request);
        } catch (AnalyticsQueryRateLimiter.AnalyticsQueryRateLimitException ex) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, ex.getMessage());
        }
    }

    /** Validate analytics CEL-over-historian expression (BL-211). */
    @PostMapping("/expression/validate")
    public AnalyticsExpressionService.ValidateResult validateExpression(@RequestBody AnalyticsExpressionRequest request) {
        return expressionService.validate(request.expression(), request.objectPath());
    }

    /** Unified Tier A analytics function catalog (BL-212a / ADR-0042). */
    @GetMapping("/catalog")
    public List<AnalyticsCatalogEntry> listCatalog() {
        return analyticsCatalogService.list();
    }

    /** Single analytics function metadata by id (BL-212a / ADR-0042). */
    @GetMapping("/catalog/{functionId}")
    public AnalyticsCatalogEntry getCatalogEntry(@PathVariable String functionId) {
        return analyticsCatalogService.get(functionId);
    }

    /** Validate analytics expression by catalog kind/context payload (BL-212a). */
    @PostMapping("/catalog/validate")
    public AnalyticsExpressionService.ValidateResult validateCatalogExpression(
            @RequestBody AnalyticsCatalogValidateRequest request
    ) {
        String objectPath = resolveObjectPath(request.context());
        return expressionService.validate(request.expression(), objectPath);
    }

    /** Evaluate analytics CEL-over-historian expression once (BL-211). */
    @PostMapping("/expression/evaluate")
    public AnalyticsExpressionService.EvaluateResult evaluateExpression(@RequestBody AnalyticsExpressionRequest request) {
        return expressionService.evaluate(request.expression(), request.objectPath(), request.asOf());
    }

    public record AnalyticsExpressionRequest(
            String expression,
            String objectPath,
            Instant asOf
    ) {
    }

    public record AnalyticsCatalogValidateRequest(
            String kind,
            String expression,
            Map<String, Object> context
    ) {
    }

    /** List active analytics event frames for a scope (BL-208). */
    @GetMapping("/frames/active")
    public List<EventFrame> listActiveFrames(@RequestParam(required = false) String scopePath) {
        return eventFrameService.listActive(scopePath);
    }

    /** Open a shift event frame from a MES {@code mes_oee_shift} row (BL-208). */
    @PostMapping("/frames/open-shift")
    public EventFrame openShiftFrame(
            @RequestParam String shiftId,
            @RequestParam String scopePath,
            @RequestParam(required = false, defaultValue = EventFrameMesShiftBridge.DEFAULT_MES_APPLICATION_ID) String applicationId
    ) {
        return eventFrameService.openMesShift(applicationId, shiftId, scopePath);
    }

    /** Open a custom analytics event frame (BL-208). */
    @PostMapping("/frames/open")
    public EventFrame openFrame(@RequestBody OpenEventFrameRequest body) {
        return eventFrameService.open(new EventFrameService.OpenEventFrameCommand(
                EventFrameType.parse(body.frameType()),
                body.scopePath(),
                body.sourcePath(),
                body.sourceKey(),
                body.label(),
                body.startedAt(),
                body.endedAt(),
                body.downtimeMinutes(),
                body.metadata()
        ));
    }

    /** Close an active analytics event frame (BL-208). */
    @PostMapping("/frames/close")
    public EventFrame closeFrame(
            @RequestParam UUID frameId,
            @RequestParam(required = false) Instant endedAt
    ) {
        return eventFrameService.close(frameId, endedAt);
    }

    /** Downtime minutes per frame for operator reports (BL-208). */
    @GetMapping("/frames/downtime-report")
    public List<EventFrameService.DowntimeFrameReportRow> downtimeReport(@RequestParam String scopePath) {
        return eventFrameService.downtimeReport(scopePath);
    }

    public record OpenEventFrameRequest(
            String frameType,
            String scopePath,
            String sourcePath,
            String sourceKey,
            String label,
            Instant startedAt,
            Instant endedAt,
            int downtimeMinutes,
            Map<String, String> metadata
    ) {
    }

    private static String resolveObjectPath(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        Object objectPath = context.get("objectPath");
        if (objectPath instanceof String value && !value.isBlank()) {
            return value;
        }
        Object path = context.get("path");
        if (path instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }

    /** Export multi-tag analytics query as CSV or Parquet-compatible JSONL (BL-206). */
    @PostMapping("/query/export")
    public ResponseEntity<byte[]> exportQuery(
            @RequestParam String format,
            @RequestBody AnalyticsQueryRequest request
    ) throws IOException {
        String normalized = AnalyticsQueryExportService.normalizeFormat(format);
        try {
            return switch (normalized) {
                case "csv" -> {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    analyticsQueryExportService.exportCsv(request, buffer);
                    yield ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analytics-query.csv\"")
                            .contentType(new MediaType("text", "csv"))
                            .body(buffer.toByteArray());
                }
                case "parquet" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analytics-query.parquet.jsonl\"")
                        .header("X-ISPF-Export-Format", "parquet-jsonl")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(analyticsQueryExportService.exportParquet(request));
                default -> throw new IllegalArgumentException("Unsupported export format: " + format);
            };
        } catch (AnalyticsQueryRateLimiter.AnalyticsQueryRateLimitException ex) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, ex.getMessage());
        }
    }
}
