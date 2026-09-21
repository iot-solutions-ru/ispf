package com.ispf.server.report;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.report.ApplicationReportStore;
import com.ispf.server.bootstrap.LabBlueprintBootstrap;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.datasource.DataSourceSqlSession;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.time.PlatformCalendarParameterEnricher;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportService {

    public static final String REPORTS_ROOT = "root.platform.reports";
    public static final String REPORT_TYPE_TREE_VARIABLES = LabBlueprintBootstrap.TREE_VARIABLES_REPORT_TYPE;

    private static final List<ReportColumn> DEFAULT_TREE_VARIABLE_COLUMNS = List.of(
            new ReportColumn("devicepath", "Device path"),
            new ReportColumn("int", "Int"),
            new ReportColumn("string", "String")
    );

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private final ObjectManager objectManager;
    private final BlueprintRegistry BlueprintRegistry;
    private final BlueprintEngine BlueprintEngine;
    private final SystemObjectStructureService structureService;
    private final ApplicationSchemaSession schemaSession;
    private final ApplicationReportStore reportStore;
    private final ReportTemplateStore templateStore;
    private final DataSourceSqlSession dataSourceSqlSession;
    private final DataSourcePathResolver dataSourcePathResolver;
    private final DataSourceObjectService dataSourceObjectService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformCalendarParameterEnricher calendarParameterEnricher;
    private final TenantLocalDataAccessGuard tenantLocalDataAccessGuard;
    private final TreeVariablesReportRows treeVariablesReportRows;

    public ReportService(
            ObjectManager objectManager,
            BlueprintRegistry BlueprintRegistry,
            BlueprintEngine BlueprintEngine,
            SystemObjectStructureService structureService,
            ApplicationSchemaSession schemaSession,
            ApplicationReportStore reportStore,
            ReportTemplateStore templateStore,
            DataSourcePathResolver dataSourcePathResolver,
            DataSourceSqlSession dataSourceSqlSession,
            DataSourceObjectService dataSourceObjectService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformCalendarParameterEnricher calendarParameterEnricher,
            TenantLocalDataAccessGuard tenantLocalDataAccessGuard,
            TreeVariablesReportRows treeVariablesReportRows
    ) {
        this.objectManager = objectManager;
        this.BlueprintRegistry = BlueprintRegistry;
        this.BlueprintEngine = BlueprintEngine;
        this.structureService = structureService;
        this.schemaSession = schemaSession;
        this.reportStore = reportStore;
        this.templateStore = templateStore;
        this.dataSourcePathResolver = dataSourcePathResolver;
        this.dataSourceSqlSession = dataSourceSqlSession;
        this.dataSourceObjectService = dataSourceObjectService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.calendarParameterEnricher = calendarParameterEnricher;
        this.tenantLocalDataAccessGuard = tenantLocalDataAccessGuard;
        this.treeVariablesReportRows = treeVariablesReportRows;
    }

    public static String reportPath(String reportId) {
        return REPORTS_ROOT + "." + sanitizeReportNodeName(reportId);
    }

    /**
     * Maps legacy application-scoped report paths (pre tree-first) to {@link #REPORTS_ROOT}.
     * Example: {@code root.platform.applications.mini-tec.reports.tec-daily-energy}
     * → {@code root.platform.reports.tec-daily-energy}.
     */
    public static String resolveReportPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        String path = rawPath.trim();
        if (!path.startsWith("root.platform.applications.") || !path.contains(".reports.")) {
            return path;
        }
        int marker = path.indexOf(".reports.");
        String reportId = path.substring(marker + ".reports.".length());
        if (reportId.isBlank() || reportId.contains(".")) {
            return path;
        }
        return reportPath(reportId);
    }

    private static String resolvePath(String rawPath) {
        return resolveReportPath(rawPath);
    }

    private static String sanitizeReportNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "node";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            return "node";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return "n_" + sanitized;
        }
        return sanitized;
    }

    public static String reportIdFromPath(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    @Transactional
    public void ensureReportsCatalog() {
        ensureReportsCatalogInternal();
    }

    private void ensureReportsCatalogInternal() {
        if (objectManager.tree().findByPath(REPORTS_ROOT).isEmpty()) {
            objectManager.create(
                    "root.platform",
                    "reports",
                    ObjectType.REPORTS,
                    "Reports",
                    "SQL reports (tree-first)",
                    null
            );
        } else {
            objectManager.reconcileType(REPORTS_ROOT, ObjectType.REPORTS);
        }
    }

    @Transactional
    public void ensureReportStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + path);
        }
        structureService.ensureReportStructure(path);
    }

    @Transactional
    public void ensureTreeVariablesReportStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + path);
        }
        if (node.getVariable("reportType").isPresent()) {
            return;
        }
        BlueprintRegistry.findByName(LabBlueprintBootstrap.TREE_VARIABLES_REPORT_MODEL).ifPresent(model -> {
            BlueprintEngine.applyBlueprint(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    @Transactional
    public void deploy(
            String dataSourcePath,
            String reportId,
            String title,
            String description,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Integer maxRows,
            Map<String, Object> defaultParameters
    ) {
        validateDataSourcePath(dataSourcePath);
        ReportSqlQuery.validateSelectQuery(query);
        ensureReportsCatalogInternal();
        String path = reportPath(reportId);
        ensureReportNode(path, title, description, "report-v1");
        ensureReportStructure(path);
        saveDefinitionInternal(
                path,
                new ReportDefinition(
                        title,
                        dataSourcePath,
                        query.trim(),
                        parameters != null ? parameters : List.of(),
                        columns != null ? columns : List.of(),
                        defaultParameters != null ? defaultParameters : Map.of(),
                        maxRows != null && maxRows > 0 ? maxRows : 1000,
                        30000,
                        ""
                )
        );
    }

    @Transactional
    public void deployTreeVariables(
            String reportId,
            String title,
            String description,
            String devicePathPattern,
            String variableName,
            List<ReportColumn> columns,
            Integer maxRows
    ) {
        ensureReportsCatalogInternal();
        String path = reportPath(reportId);
        ensureReportNode(path, title, description, LabBlueprintBootstrap.TREE_VARIABLES_REPORT_MODEL);
        ensureTreeVariablesReportStructure(path);
        saveTreeVariablesDefinitionInternal(
                path,
                title,
                devicePathPattern,
                variableName,
                columns != null && !columns.isEmpty() ? columns : DEFAULT_TREE_VARIABLE_COLUMNS,
                maxRows != null && maxRows > 0 ? maxRows : 1000,
                30000,
                ""
        );
    }
    @Deprecated
    @Transactional
    public void deployLegacyApp(
            String appId,
            String reportId,
            String title,
            String description,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Integer maxRows,
            Map<String, Object> defaultParameters
    ) {
        String dataSourcePath = dataSourceObjectService.pathForNodeName(appId);
        dataSourceObjectService.ensureDataSource(appId, appId, inferSchemaForApp(appId), "Legacy app data source");
        deploy(dataSourcePath, reportId, title, description, query, parameters, columns, maxRows, defaultParameters);
    }

    public ReportView getReport(String path) {
        String resolved = resolvePath(path);
        PlatformObject node = objectManager.require(resolved);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + resolved);
        }
        return toView(resolved, node);
    }

    @Transactional
    public ReportView saveDefinition(String path, SaveReportDefinitionRequest request) {
        String resolved = resolvePath(path);
        PlatformObject node = objectManager.require(resolved);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + resolved);
        }
        ReportSqlQuery.validateSelectQuery(request.query());
        ReportView current = toView(resolved, node);
        String dataSourcePath = resolveDataSourcePathForSave(request, current);
        validateDataSourcePath(dataSourcePath);
        ReportDefinition definition = new ReportDefinition(
                request.title() != null ? request.title() : current.title(),
                dataSourcePath,
                request.query().trim(),
                request.parameters() != null ? request.parameters() : current.parameters(),
                request.columns() != null ? request.columns() : current.columns(),
                request.defaultParameters() != null ? request.defaultParameters() : current.defaultParameters(),
                request.maxRows() != null ? request.maxRows() : current.maxRows(),
                request.refreshIntervalMs() != null ? request.refreshIntervalMs() : current.refreshIntervalMs(),
                request.layout() != null ? request.layout() : current.layout()
        );
        saveDefinitionInternal(resolved, definition);
        return getReport(resolved);
    }

    @Transactional
    public ReportView saveTreeVariablesDefinition(
            String path,
            SaveTreeVariablesDefinitionRequest request
    ) {
        String resolved = resolvePath(path);
        PlatformObject node = objectManager.require(resolved);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + resolved);
        }
        ensureTreeVariablesReportStructure(resolved);
        ReportView current = getReport(resolved);
        String devicePathPattern = request.devicePathPattern() != null
                ? request.devicePathPattern().trim()
                : "";
        String variableName = request.variableName() != null ? request.variableName().trim() : "";
        if (devicePathPattern.isBlank()) {
            throw new IllegalArgumentException("Report devicePathPattern is required for tree-variables reports");
        }
        if (variableName.isBlank()) {
            throw new IllegalArgumentException("Report variableName is required for tree-variables reports");
        }
        List<ReportColumn> columns = request.columns() != null && !request.columns().isEmpty()
                ? request.columns()
                : current.columns();
        saveTreeVariablesDefinitionInternal(
                resolved,
                request.title() != null ? request.title() : current.title(),
                devicePathPattern,
                variableName,
                columns,
                request.maxRows() != null ? request.maxRows() : current.maxRows(),
                request.refreshIntervalMs() != null ? request.refreshIntervalMs() : current.refreshIntervalMs(),
                current.layout()
        );
        return getReport(resolved);
    }

    @Transactional
    public ReportView saveLayout(String path, String layoutJson) {
        String resolved = resolvePath(path);
        getReport(resolved);
        setString(resolved, "layout", layoutJson != null ? layoutJson : "");
        return getReport(resolved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> run(String path, Map<String, Object> parameters) {
        ReportView report = getReport(path);
        return runDefinition(report, parameters);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> runByApp(String appId, String reportId, Map<String, Object> parameters) {
        String path = reportPath(reportId);
        if (objectManager.tree().findByPath(path).isEmpty()) {
            return runLegacy(appId, reportId, parameters);
        }
        ReportView report = getReport(path);
        return runDefinition(report, parameters);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String path, Map<String, Object> parameters) {
        Map<String, Object> result = run(path, parameters);
        return ReportTableExport.toCsv(result);
    }

    @Transactional(readOnly = true)
    public byte[] exportHtmlTable(String path, Map<String, Object> parameters) {
        Map<String, Object> result = run(path, parameters);
        return ReportTableExport.toHtmlTable(result);
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsxTable(String path, Map<String, Object> parameters) {
        Map<String, Object> result = run(path, parameters);
        return ReportTableExport.toXlsxTable(result);
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsTable(String path, Map<String, Object> parameters) {
        Map<String, Object> result = run(path, parameters);
        return ReportTableExport.toXlsTable(result);
    }

    @Transactional(readOnly = true)
    public boolean hasTemplate(String path) {
        String resolved = resolvePath(path);
        getReport(resolved);
        return templateStore.exists(resolved);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsvByApp(String appId, String reportId, Map<String, Object> parameters) {
        Map<String, Object> result = runByApp(appId, reportId, parameters);
        return ReportTableExport.toCsv(result);
    }

    @Transactional
    public ReportView saveTemplate(String path, String format, byte[] content) {
        String resolved = resolvePath(path);
        PlatformObject node = objectManager.require(resolved);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + resolved);
        }
        ReportTemplateStore.validateFormat(format);
        templateStore.save(resolved, format, content);
        setString(resolved, "templateFormat", format.trim().toLowerCase());
        return getReport(resolved);
    }

    @Transactional(readOnly = true)
    public Optional<ReportTemplateStore.StoredTemplate> getTemplate(String path) {
        String resolved = resolvePath(path);
        getReport(resolved);
        return templateStore.find(resolved);
    }

    @Transactional
    public ReportView deleteTemplate(String path) {
        String resolved = resolvePath(path);
        PlatformObject node = objectManager.require(resolved);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + resolved);
        }
        templateStore.delete(resolved);
        setString(resolved, "templateFormat", "");
        return getReport(resolved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listByDataSource(String dataSourcePath) {
        validateDataSourcePath(dataSourcePath);
        ensureReportsCatalogInternal();
        List<Map<String, Object>> summaries = new ArrayList<>();
        if (objectManager.tree().findByPath(REPORTS_ROOT).isPresent()) {
            for (PlatformObject child : objectManager.tree().childrenOf(REPORTS_ROOT)) {
                if (child.type() != ObjectType.REPORT) {
                    continue;
                }
                ReportView view = toView(child.path(), child);
                if (dataSourcePath.equals(view.dataSourcePath())) {
                    summaries.add(toSummary(view));
                }
            }
        }
        return summaries;
    }

    private Map<String, Object> runDefinition(ReportView report, Map<String, Object> parameters) {
        if (REPORT_TYPE_TREE_VARIABLES.equals(report.reportType())) {
            return runTreeVariablesDefinition(report);
        }

        Map<String, Object> effective = ReportSqlQuery.effectiveParameters(
                report.parameters(),
                report.defaultParameters(),
                calendarParameterEnricher.enrich(parameters)
        );
        List<Object> paramValues = ReportSqlQuery.bindQueryParameters(report.query(), report.parameters(), effective);

        ReportSqlQuery.validateSelectQuery(report.query());
        validateDataSourcePath(report.dataSourcePath());
        List<Map<String, Object>>[] result = new List[1];
        if (report.dataSourcePath() != null && !report.dataSourcePath().isBlank()
                && dataSourcePathResolver.isExternal(report.dataSourcePath())) {
            dataSourceSqlSession.runWithDataSource(report.dataSourcePath(), jdbc ->
                    result[0] = jdbc.queryForList(report.query(), paramValues.toArray()));
        } else {
            tenantLocalDataAccessGuard.requireExternalDataAccess();
            String schemaName = dataSourcePathResolver.resolveSchemaForReport(
                    report.dataSourcePath(),
                    report.legacyAppId()
            );
            schemaSession.runInSchema(schemaName, () ->
                    result[0] = jdbcTemplate.queryForList(report.query(), paramValues.toArray()));
        }

        List<Map<String, Object>> rows = result[0];
        boolean truncated = rows.size() > report.maxRows();
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, report.maxRows()));
        }
        rows = ReportSqlQuery.normalizeRowKeys(rows);
        return Map.of(
                "path", report.path(),
                "reportId", reportIdFromPath(report.path()),
                "title", report.title(),
                "columns", columnMaps(report.columns()),
                "rows", rows,
                "rowCount", rows.size(),
                "truncated", truncated
        );
    }

    private Map<String, Object> runLegacy(String appId, String reportId, Map<String, Object> parameters) {
        tenantLocalDataAccessGuard.requireExternalDataAccess();
        ApplicationReportStore.DeployedReport report = reportStore.find(appId, reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        List<String> paramNames = deserializeStringList(report.parametersJson());
        Map<String, Object> effective = ReportSqlQuery.effectiveParameters(
                paramNames,
                Map.of(),
                calendarParameterEnricher.enrich(parameters)
        );
        List<Object> paramValues = ReportSqlQuery.bindQueryParameters(report.querySql(), paramNames, effective);
        String schemaName = dataSourcePathResolver.resolveSchemaForReport(null, appId);

        ReportSqlQuery.validateSelectQuery(report.querySql());
        List<Map<String, Object>>[] result = new List[1];
        schemaSession.runInSchema(schemaName, () ->
                result[0] = jdbcTemplate.queryForList(report.querySql(), paramValues.toArray())
        );

        List<Map<String, Object>> rows = result[0];
        boolean truncated = rows.size() > report.maxRows();
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, report.maxRows()));
        }
        List<Map<String, String>> columns = deserializeColumns(report.columnsJson());
        rows = ReportSqlQuery.normalizeRowKeys(rows);
        return Map.of(
                "reportId", report.reportId(),
                "title", report.title(),
                "columns", columns,
                "rows", rows,
                "rowCount", rows.size(),
                "truncated", truncated
        );
    }

    private Map<String, Object> runTreeVariablesDefinition(ReportView report) {
        List<Map<String, Object>> rows = treeVariablesReportRows.collect(report.devicePathPattern(), report.variableName());
        boolean truncated = rows.size() > report.maxRows();
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, report.maxRows()));
        }
        rows = ReportSqlQuery.normalizeRowKeys(rows);
        return Map.of(
                "path", report.path(),
                "reportId", reportIdFromPath(report.path()),
                "title", report.title(),
                "reportType", REPORT_TYPE_TREE_VARIABLES,
                "columns", columnMaps(report.columns()),
                "rows", rows,
                "rowCount", rows.size(),
                "truncated", truncated
        );
    }

    private void ensureReportNode(String path, String title, String description, String templateId) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.updateInfo(path, title, description != null ? description : "");
            objectManager.reconcileType(path, ObjectType.REPORT);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        String parentPath = path.substring(0, lastDot);
        String name = path.substring(lastDot + 1);
        if (objectManager.tree().findByPath(parentPath).isEmpty()) {
            ensureReportsCatalogInternal();
        }
        objectManager.create(
                parentPath,
                name,
                ObjectType.REPORT,
                title,
                description != null ? description : "",
                templateId
        );
    }

    private void saveTreeVariablesDefinitionInternal(
            String path,
            String title,
            String devicePathPattern,
            String variableName,
            List<ReportColumn> columns,
            int maxRows,
            int refreshIntervalMs,
            String layout
    ) {
        setString(path, "title", title);
        setString(path, "reportType", REPORT_TYPE_TREE_VARIABLES);
        setString(path, "devicePathPattern", devicePathPattern);
        setString(path, "variableName", variableName);
        setString(path, "columns", serialize(columns));
        setString(path, "defaultParameters", "{}");
        setInteger(path, "maxRows", maxRows);
        setInteger(path, "refreshIntervalMs", refreshIntervalMs);
        setString(path, "layout", layout);
    }

    private void saveDefinitionInternal(String path, ReportDefinition definition) {
        setString(path, "title", definition.title());
        setString(path, "dataSourcePath", definition.dataSourcePath());
        setString(path, "query", definition.query());
        setString(path, "parameters", serialize(definition.parameters()));
        setString(path, "columns", serialize(definition.columns()));
        setString(path, "defaultParameters", serialize(definition.defaultParameters()));
        setInteger(path, "maxRows", definition.maxRows());
        setInteger(path, "refreshIntervalMs", definition.refreshIntervalMs());
        setString(path, "layout", definition.layout());
    }

    private ReportView toView(String path, PlatformObject node) {
        String dataSourcePath = readString(node, "dataSourcePath").orElse("");
        String legacyAppId = readString(node, "appId").orElse("");
        if (dataSourcePath.isBlank() && !legacyAppId.isBlank()) {
            dataSourcePath = dataSourceObjectService.pathForNodeName(legacyAppId);
        }
        return new ReportView(
                path,
                readString(node, "title").orElse(node.displayName()),
                dataSourcePath,
                legacyAppId,
                readString(node, "query").orElse(""),
                readString(node, "reportType").orElse(""),
                readString(node, "devicePathPattern").orElse(""),
                readString(node, "variableName").orElse(""),
                deserializeStringList(readString(node, "parameters").orElse("[]")),
                deserializeReportColumns(readString(node, "columns").orElse("[]")),
                deserializeObjectMap(readString(node, "defaultParameters").orElse("{}")),
                readInteger(node, "maxRows").orElse(1000),
                readInteger(node, "refreshIntervalMs").orElse(30000),
                readString(node, "templateFormat").orElse(""),
                readString(node, "layout").orElse(""),
                templateStore.exists(path)
        );
    }

    private String resolveDataSourcePathForSave(SaveReportDefinitionRequest request, ReportView current) {
        if (request.dataSourcePath() != null && !request.dataSourcePath().isBlank()) {
            return request.dataSourcePath();
        }
        if (request.appId() != null && !request.appId().isBlank()) {
            return dataSourceObjectService.pathForNodeName(request.appId());
        }
        if (current.dataSourcePath() != null && !current.dataSourcePath().isBlank()) {
            return current.dataSourcePath();
        }
        if (current.legacyAppId() != null && !current.legacyAppId().isBlank()) {
            return dataSourceObjectService.pathForNodeName(current.legacyAppId());
        }
        throw new IllegalArgumentException("Report dataSourcePath is required");
    }

    private void validateDataSourcePath(String dataSourcePath) {
        if (dataSourcePath == null || dataSourcePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Report dataSourcePath is required — e.g. root.platform.data-sources.demo"
            );
        }
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(dataSourcePath);
    }

    private String inferSchemaForApp(String appId) {
        return dataSourcePathResolver.resolveSchemaForReport(
                dataSourceObjectService.pathForNodeName(appId),
                appId
        );
    }

    private Map<String, Object> toSummary(ReportView view) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportId", reportIdFromPath(view.path()));
        summary.put("path", view.path());
        summary.put("title", view.title());
        summary.put("dataSourcePath", view.dataSourcePath());
        summary.put("parameters", view.parameters());
        summary.put("columns", columnMaps(view.columns()));
        summary.put("maxRows", view.maxRows());
        return summary;
    }

    private static List<Map<String, String>> columnMaps(List<ReportColumn> columns) {
        return columns.stream()
                .map(col -> Map.of("field", col.field(), "label", col.label()))
                .toList();
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(STRING_SCHEMA, Map.of("value", value != null ? value : ""))
        );
    }

    private void setInteger(String path, String variable, int value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(INTEGER_SCHEMA, Map.of("value", value))
        );
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize report metadata", ex);
        }
    }

    private List<String> deserializeStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid report parameters JSON", ex);
        }
    }

    private List<Map<String, String>> deserializeColumns(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid report columns JSON", ex);
        }
    }

    private List<ReportColumn> deserializeReportColumns(String json) {
        List<Map<String, String>> raw = deserializeColumns(json);
        return raw.stream()
                .map(col -> new ReportColumn(col.get("field"), col.get("label")))
                .toList();
    }

    private Map<String, Object> deserializeObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid defaultParameters JSON", ex);
        }
    }

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }

    private static Optional<Integer> readInteger(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(value -> {
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                    return Integer.parseInt(String.valueOf(value));
                });
    }

    public record ReportColumn(String field, String label) {
    }

    public record ReportDefinition(
            String title,
            String dataSourcePath,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Map<String, Object> defaultParameters,
            int maxRows,
            int refreshIntervalMs,
            String layout
    ) {
    }

    public record ReportView(
            String path,
            String title,
            String dataSourcePath,
            String legacyAppId,
            String query,
            String reportType,
            String devicePathPattern,
            String variableName,
            List<String> parameters,
            List<ReportColumn> columns,
            Map<String, Object> defaultParameters,
            int maxRows,
            int refreshIntervalMs,
            String templateFormat,
            String layout,
            boolean hasTemplate
    ) {
    }

    public record SaveReportDefinitionRequest(
            String title,
            String dataSourcePath,
            String appId,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Map<String, Object> defaultParameters,
            Integer maxRows,
            Integer refreshIntervalMs,
            String layout
    ) {
    }

    public record SaveTreeVariablesDefinitionRequest(
            String title,
            String devicePathPattern,
            String variableName,
            List<ReportColumn> columns,
            Integer maxRows,
            Integer refreshIntervalMs
    ) {
    }
}
