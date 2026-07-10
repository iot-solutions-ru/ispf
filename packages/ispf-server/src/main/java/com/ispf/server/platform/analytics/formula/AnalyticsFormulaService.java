package com.ispf.server.platform.analytics.formula;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.analytics.AnalyticsFormulasConstants;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.application.tree.ApplicationObjectTreeService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AnalyticsFormulaService {

    private static final DataSchema FORMULAS_SCHEMA = DataSchema.builder("analyticsFormulasJson")
            .field("value", FieldType.STRING)
            .build();

    private static final Pattern FORMULA_ID = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,63}$");

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public AnalyticsFormulaService(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsFormula> listSiteFormulas() {
        return readFormulas(AnalyticsFormulasConstants.PLATFORM_PATH);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsFormula> listAppFormulas(String appId) {
        return readFormulas(resolveAppPath(appId));
    }

    @Transactional(readOnly = true)
    public List<AnalyticsFormula> listAllForCatalog() {
        List<AnalyticsFormula> formulas = new ArrayList<>(listSiteFormulas());
        if (objectManager.tree().findByPath(ApplicationObjectTreeService.APPLICATIONS_ROOT).isEmpty()) {
            return List.copyOf(formulas);
        }
        for (PlatformObject child : objectManager.tree().childrenOf(ApplicationObjectTreeService.APPLICATIONS_ROOT)) {
            formulas.addAll(readFormulas(child.path()));
        }
        return List.copyOf(formulas);
    }

    @Transactional(readOnly = true)
    public AnalyticsFormula get(String id, String scope, String appId) {
        return find(id, scope, appId).orElseThrow(() ->
                new IllegalArgumentException("Analytics formula not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<AnalyticsFormula> find(String id, String scope, String appId) {
        String normalizedScope = normalizeScope(scope);
        if (AnalyticsFormula.SCOPE_APP.equals(normalizedScope)) {
            return readFormulas(resolveAppPath(appId)).stream()
                    .filter(formula -> formula.id().equals(id))
                    .findFirst();
        }
        return listSiteFormulas().stream()
                .filter(formula -> formula.id().equals(id))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<AnalyticsFormula> findForCatalog(String id) {
        return listAllForCatalog().stream()
                .filter(formula -> formula.id().equals(id))
                .findFirst();
    }

    @Transactional
    public AnalyticsFormula create(AnalyticsFormula draft) {
        AnalyticsFormula normalized = normalizeDraft(draft, true);
        String objectPath = storagePath(normalized.scope(), normalized.appId());
        List<AnalyticsFormula> formulas = new ArrayList<>(readFormulas(objectPath));
        if (formulas.stream().anyMatch(existing -> existing.id().equals(normalized.id()))) {
            throw new IllegalArgumentException("Analytics formula already exists: " + normalized.id());
        }
        formulas.add(normalized);
        writeFormulas(objectPath, formulas);
        return normalized;
    }

    @Transactional
    public AnalyticsFormula update(String id, AnalyticsFormula draft, String scope, String appId) {
        if (id == null || !id.equals(draft.id())) {
            throw new IllegalArgumentException("Formula id mismatch");
        }
        AnalyticsFormula normalized = normalizeDraft(draft, false);
        String normalizedScope = normalizeScope(scope);
        String objectPath = storagePath(normalizedScope, appId);
        List<AnalyticsFormula> formulas = new ArrayList<>(readFormulas(objectPath));
        int index = -1;
        for (int i = 0; i < formulas.size(); i++) {
            if (formulas.get(i).id().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Analytics formula not found: " + id);
        }
        AnalyticsFormula previous = formulas.get(index);
        int version = Math.max(previous.version(), normalized.version()) + 1;
        AnalyticsFormula saved = new AnalyticsFormula(
                normalized.id(),
                normalized.displayName(),
                normalized.kind(),
                normalized.expression(),
                normalized.parameters(),
                normalized.createdBy() != null ? normalized.createdBy() : previous.createdBy(),
                version,
                normalizedScope,
                AnalyticsFormula.SCOPE_APP.equals(normalizedScope) ? appId : null
        );
        formulas.set(index, saved);
        writeFormulas(objectPath, formulas);
        return saved;
    }

    @Transactional
    public void delete(String id, String scope, String appId) {
        String objectPath = storagePath(normalizeScope(scope), appId);
        List<AnalyticsFormula> formulas = readFormulas(objectPath).stream()
                .filter(formula -> !formula.id().equals(id))
                .toList();
        if (formulas.size() == readFormulas(objectPath).size()) {
            throw new IllegalArgumentException("Analytics formula not found: " + id);
        }
        writeFormulas(objectPath, formulas);
    }

    @Transactional
    public String expand(String id, Map<String, String> parameters, String scope, String appId) {
        AnalyticsFormula formula = get(id, scope, appId);
        return AnalyticsFormulaExpander.expand(formula.expression(), parameters);
    }

    @Transactional
    public void mergeAppBundleFormulas(String appId, List<AnalyticsFormula> bundleFormulas) {
        if (bundleFormulas == null || bundleFormulas.isEmpty()) {
            return;
        }
        String objectPath = resolveAppPath(appId);
        objectManager.require(objectPath);
        Map<String, AnalyticsFormula> merged = new LinkedHashMap<>();
        for (AnalyticsFormula existing : readFormulas(objectPath)) {
            merged.put(existing.id(), existing);
        }
        for (AnalyticsFormula incoming : bundleFormulas) {
            AnalyticsFormula normalized = normalizeDraft(
                    new AnalyticsFormula(
                            incoming.id(),
                            incoming.displayName(),
                            incoming.kind(),
                            incoming.expression(),
                            incoming.parameters(),
                            incoming.createdBy(),
                            incoming.version() > 0 ? incoming.version() : 1,
                            AnalyticsFormula.SCOPE_APP,
                            appId
                    ),
                    true
            );
            merged.put(normalized.id(), normalized);
        }
        writeFormulas(objectPath, List.copyOf(merged.values()));
    }

    private List<AnalyticsFormula> readFormulas(String objectPath) {
        Optional<PlatformObject> object = objectManager.tree().findByPath(objectPath);
        if (object.isEmpty()) {
            return List.of();
        }
        return object.get().getVariable(AnalyticsFormulasConstants.FORMULAS_VARIABLE)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(json -> !json.isBlank())
                .map(this::parseFormulas)
                .orElse(List.of());
    }

    private List<AnalyticsFormula> parseFormulas(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid analytics formulas JSON: " + ex.getMessage());
        }
    }

    private void writeFormulas(String objectPath, List<AnalyticsFormula> formulas) {
        try {
            String json = objectMapper.writeValueAsString(formulas);
            DataRecord record = DataRecord.single(FORMULAS_SCHEMA, Map.of("value", json));
            objectManager.upsertSystemVariable(
                    objectPath,
                    AnalyticsFormulasConstants.FORMULAS_VARIABLE,
                    FORMULAS_SCHEMA,
                    record
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist analytics formulas", ex);
        }
    }

    private AnalyticsFormula normalizeDraft(AnalyticsFormula draft, boolean creating) {
        if (draft == null) {
            throw new IllegalArgumentException("Formula is required");
        }
        String id = safe(draft.id());
        if (!FORMULA_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid formula id: " + id);
        }
        String displayName = safe(draft.displayName());
        if (displayName.isBlank()) {
            displayName = id;
        }
        String kind = normalizeKind(draft.kind());
        String expression = safe(draft.expression());
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Formula expression is required");
        }
        List<AnalyticsFormulaParameter> parameters = normalizeParameters(draft.parameters(), expression);
        String scope = normalizeScope(draft.scope());
        String appId = AnalyticsFormula.SCOPE_APP.equals(scope) ? safe(draft.appId()) : null;
        if (AnalyticsFormula.SCOPE_APP.equals(scope) && (appId == null || appId.isBlank())) {
            throw new IllegalArgumentException("appId is required for app-scoped formulas");
        }
        int version = creating ? 1 : Math.max(draft.version(), 1);
        return new AnalyticsFormula(
                id,
                displayName,
                kind,
                expression,
                parameters,
                blankToNull(draft.createdBy()),
                version,
                scope,
                appId
        );
    }

    private static List<AnalyticsFormulaParameter> normalizeParameters(
            List<AnalyticsFormulaParameter> parameters,
            String expression
    ) {
        if (parameters != null && !parameters.isEmpty()) {
            return parameters.stream()
                    .map(param -> new AnalyticsFormulaParameter(
                            safe(param.name()),
                            safe(param.type()).isBlank() ? "string" : param.type(),
                            param.required(),
                            blankToNull(param.description()),
                            blankToNull(param.defaultValue())
                    ))
                    .filter(param -> !param.name().isBlank())
                    .toList();
        }
        return AnalyticsFormulaExpander.detectParameterNames(expression).stream()
                .map(name -> new AnalyticsFormulaParameter(name, "string", true, null, null))
                .toList();
    }

    private static String normalizeKind(String kind) {
        String normalized = safe(kind).toLowerCase(Locale.ROOT);
        if (AnalyticsFormula.KIND_REACTIVE.equals(normalized)) {
            return AnalyticsFormula.KIND_REACTIVE;
        }
        return AnalyticsFormula.KIND_HISTORIAN;
    }

    private static String normalizeScope(String scope) {
        String normalized = safe(scope).toLowerCase(Locale.ROOT);
        if (AnalyticsFormula.SCOPE_APP.equals(normalized)) {
            return AnalyticsFormula.SCOPE_APP;
        }
        return AnalyticsFormula.SCOPE_SITE;
    }

    private static String storagePath(String scope, String appId) {
        if (AnalyticsFormula.SCOPE_APP.equals(scope)) {
            return resolveAppPath(appId);
        }
        return AnalyticsFormulasConstants.PLATFORM_PATH;
    }

    private static String resolveAppPath(String appId) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId is required");
        }
        return ApplicationObjectTreeService.APPLICATIONS_ROOT + "." + sanitizeNodeName(appId);
    }

    private static String sanitizeNodeName(String value) {
        return value.trim().replace('.', '-');
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
