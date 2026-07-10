package com.ispf.server.platform.analytics;

import com.ispf.expression.BindingExpressionValidator;
import com.ispf.server.history.HistorianQueryMetricsRecorder;
import com.ispf.server.config.VariableHistorySloProperties;
import com.ispf.server.config.AnalyticsSloProperties;
import com.ispf.server.platform.analytics.AnalyticsDerivedTagService;
import com.ispf.server.platform.analytics.engine.AnalyticsBackfillService;
import com.ispf.server.history.HistorianRollupMaterializerService;
import com.ispf.server.platform.analytics.frames.EventFrame;
import com.ispf.server.platform.analytics.frames.EventFrameMesShiftBridge;
import com.ispf.server.platform.analytics.frames.EventFrameService;
import com.ispf.server.platform.analytics.frames.EventFrameType;
import com.ispf.server.platform.analytics.catalog.AnalyticsCatalogEntry;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagCatalogEntry;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineService;
import com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService;
import com.ispf.server.platform.analytics.formula.AnalyticsFormula;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaService;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaUpdateResponse;
import com.ispf.server.platform.analytics.formula.BindingFormulaRebindService;
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
    private final AnalyticsEngineService analyticsEngineService;
    private final AnalyticsCatalogService analyticsCatalogService;
    private final AnalyticsExpressionService expressionService;
    private final AnalyticsFormulaService formulaService;
    private final BindingFormulaRebindService bindingFormulaRebindService;

    public PlatformAnalyticsController(
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
            AnalyticsEngineService analyticsEngineService,
            AnalyticsCatalogService analyticsCatalogService,
            AnalyticsExpressionService expressionService,
            AnalyticsFormulaService formulaService,
            BindingFormulaRebindService bindingFormulaRebindService
    ) {
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
        this.analyticsEngineService = analyticsEngineService;
        this.analyticsCatalogService = analyticsCatalogService;
        this.expressionService = expressionService;
        this.formulaService = formulaService;
        this.bindingFormulaRebindService = bindingFormulaRebindService;
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

    /** Dry-run historian tag evaluation via analytics engine (binding rules + builtins). */
    @GetMapping("/tags/evaluate")
    public AnalyticsEngineService.TagProbeResult evaluateTag(
            @RequestParam String path,
            @RequestParam(required = false) Instant asOf
    ) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        return analyticsEngineService.probeTag(path, asOf);
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

    /** Materializer health snapshot for Enterprise L lab gates (BL-210). */
    @GetMapping("/rollups/materializer/status")
    public Map<String, Object> materializerStatus() {
        HistorianRollupMaterializerService.TickResult tick = rollupMaterializerService.lastTick();
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("enabled", rollupMaterializerService.isEnabled());
        payload.put("ran", tick.ran());
        payload.put("subscriptions", tick.subscriptions());
        payload.put("bucketsWritten", tick.bucketsWritten());
        payload.put("maxLagMs", tick.maxLagMs());
        payload.put("latencyMs", tick.latencyMs());
        payload.put("skipReason", tick.skipReason());
        payload.put("maxLagSecondsSlo", analyticsSloProperties.getMaterializerMaxLagSeconds());
        return payload;
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
        if ("reactive".equalsIgnoreCase(request.kind())) {
            return validateReactiveExpression(request.expression());
        }
        String objectPath = resolveObjectPath(request.context());
        return expressionService.validate(request.expression(), objectPath);
    }

    private static AnalyticsExpressionService.ValidateResult validateReactiveExpression(String expression) {
        try {
            BindingExpressionValidator.validateOrThrow(expression);
            String normalized = expression == null ? "" : expression.trim();
            return new AnalyticsExpressionService.ValidateResult(true, normalized, List.of(), List.of());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new AnalyticsExpressionService.ValidateResult(false, null, List.of(), List.of(message));
        }
    }

    /** List Tier B user-defined analytics formulas (BL-214). */
    @GetMapping("/formulas")
    public List<AnalyticsFormula> listFormulas(
            @RequestParam(required = false, defaultValue = AnalyticsFormula.SCOPE_SITE) String scope,
            @RequestParam(required = false) String appId
    ) {
        if (AnalyticsFormula.SCOPE_APP.equalsIgnoreCase(scope)) {
            return formulaService.listAppFormulas(appId);
        }
        return formulaService.listSiteFormulas();
    }

    /** Get a user-defined analytics formula by id (BL-214). */
    @GetMapping("/formulas/{formulaId}")
    public AnalyticsFormula getFormula(
            @PathVariable String formulaId,
            @RequestParam(required = false, defaultValue = AnalyticsFormula.SCOPE_SITE) String scope,
            @RequestParam(required = false) String appId
    ) {
        return formulaService.get(formulaId, scope, appId);
    }

    /** Create a Tier B analytics formula (BL-214). */
    @PostMapping("/formulas")
    public AnalyticsFormula createFormula(@RequestBody AnalyticsFormula formula) {
        return formulaService.create(formula);
    }

    /** Update a Tier B analytics formula (BL-214). Rebinds binding rules that reference it. */
    @PutMapping("/formulas/{formulaId}")
    public AnalyticsFormulaUpdateResponse updateFormula(
            @PathVariable String formulaId,
            @RequestParam(required = false, defaultValue = AnalyticsFormula.SCOPE_SITE) String scope,
            @RequestParam(required = false) String appId,
            @RequestBody AnalyticsFormula formula
    ) {
        AnalyticsFormula saved = formulaService.update(formulaId, formula, scope, appId);
        int reboundRules = bindingFormulaRebindService.rebindFormulaReferences(formulaId, scope, appId);
        return new AnalyticsFormulaUpdateResponse(saved, reboundRules);
    }

    /** Delete a Tier B analytics formula (BL-214). */
    @DeleteMapping("/formulas/{formulaId}")
    public void deleteFormula(
            @PathVariable String formulaId,
            @RequestParam(required = false, defaultValue = AnalyticsFormula.SCOPE_SITE) String scope,
            @RequestParam(required = false) String appId
    ) {
        formulaService.delete(formulaId, scope, appId);
    }

    /** Expand a formula template with parameter values (BL-214). */
    @PostMapping("/formulas/{formulaId}/expand")
    public AnalyticsFormulaExpandResponse expandFormula(
            @PathVariable String formulaId,
            @RequestBody AnalyticsFormulaExpandRequest request
    ) {
        String scope = request.scope() != null ? request.scope() : AnalyticsFormula.SCOPE_SITE;
        String expression = formulaService.expand(formulaId, request.parameters(), scope, request.appId());
        return new AnalyticsFormulaExpandResponse(expression);
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

    public record AnalyticsFormulaExpandRequest(
            Map<String, String> parameters,
            String scope,
            String appId
    ) {
    }

    public record AnalyticsFormulaExpandResponse(String expression) {
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
