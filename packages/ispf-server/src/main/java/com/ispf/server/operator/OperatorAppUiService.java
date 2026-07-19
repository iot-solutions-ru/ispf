package com.ispf.server.operator;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.tenant.TenantPaths;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRoot;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class OperatorAppUiService {

    private static final String PLATFORM_APP_ID = "platform";

    private final OperatorAppUiStore store;
    private final ApplicationDataStore applicationDataStore;
    private final ApplicationBundleDeployService bundleDeployService;
    private final OperatorAppObjectTreeService objectTreeService;
    private final ObjectMapper objectMapper;
    private final BootstrapProperties bootstrapProperties;
    private final TenantScopeService tenantScopeService;

    public OperatorAppUiService(
            OperatorAppUiStore store,
            ApplicationDataStore applicationDataStore,
            ApplicationBundleDeployService bundleDeployService,
            OperatorAppObjectTreeService objectTreeService,
            ObjectMapper objectMapper,
            BootstrapProperties bootstrapProperties,
            TenantScopeService tenantScopeService
    ) {
        this.store = store;
        this.applicationDataStore = applicationDataStore;
        this.bundleDeployService = bundleDeployService;
        this.objectTreeService = objectTreeService;
        this.objectMapper = objectMapper;
        this.bootstrapProperties = bootstrapProperties;
        this.tenantScopeService = tenantScopeService;
    }

    public boolean isTenantScoped(Authentication authentication) {
        return tenantScopeService.resolveTenantId(authentication).isPresent();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDefaults() throws Exception {
        if (!bootstrapProperties.isFixturesEnabled()) {
            removeFixtureDefaultsIfPresent();
            return;
        }
        if (!bootstrapProperties.shouldSeedGeneralReferenceDemos()) {
            removeFixtureDefaultsIfPresent();
            return;
        }
        if (store.findByAppId(PLATFORM_APP_ID).isPresent()) {
            return;
        }
        List<Map<String, String>> dashboards = List.of(
                Map.of(
                        "path", "root.platform.dashboards.snmp-host-monitoring",
                        "title", "SNMP Host Monitoring"
                ),
                Map.of(
                        "path", "root.platform.dashboards.demo-sensor",
                        "title", "Demo Sensor"
                )
        );
        store.upsert(new OperatorAppUiStore.OperatorAppUiRecord(
                PLATFORM_APP_ID,
                "Platform HMI",
                "root.platform.dashboards.snmp-host-monitoring",
                objectMapper.writeValueAsString(dashboards),
                null,
                Instant.now()
        ));
        objectTreeService.syncAll();
    }

    private void removeFixtureDefaultsIfPresent() throws Exception {
        if (store.findByAppId(PLATFORM_APP_ID).isEmpty()) {
            return;
        }
        store.deleteByAppId(PLATFORM_APP_ID);
        objectTreeService.syncAll();
    }

    public List<Map<String, Object>> listApps() {
        return listApps(SecurityContextHolder.getContext().getAuthentication());
    }

    public List<Map<String, Object>> listApps(Authentication authentication) {
        Optional<String> tenantId = tenantScopeService.resolveTenantId(authentication);
        Map<String, Map<String, Object>> byId = new TreeMap<>();
        for (OperatorAppUiStore.OperatorAppUiRecord record : store.listAll()) {
            if (tenantId.isPresent() && !belongsToTenant(record, tenantId.get())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("appId", record.appId());
            entry.put("title", record.title());
            entry.put("source", "operator-apps");
            byId.put(record.appId(), entry);
        }
        if (tenantId.isPresent()) {
            // Bundle-installed apps live on the global platform catalog — hide from tenants.
            return new ArrayList<>(byId.values());
        }
        for (Map<String, Object> app : applicationDataStore.listAllApps()) {
            String appId = String.valueOf(app.get("app_id"));
            if (byId.containsKey(appId) || !bundleDeployService.supportsOperatorUi(appId)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("appId", appId);
            entry.put("title", String.valueOf(app.get("display_name")));
            entry.put("source", "bundle");
            byId.put(appId, entry);
        }
        return new ArrayList<>(byId.values());
    }

    public Map<String, Object> createApp(String appId, String title) throws Exception {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId is required");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String resolvedAppId = resolveTenantAppId(appId.trim(), authentication);
        if (store.findByAppId(resolvedAppId).isPresent()) {
            throw new IllegalArgumentException("Operator app already exists: " + resolvedAppId);
        }
        String resolvedTitle = title != null && !title.isBlank() ? title.trim() : appId.trim();
        // Marker default so tenant-scoped listApps can attribute ownership before dashboards are wired.
        String ownershipMarker = tenantScopeService.resolveTenantId(authentication)
                .map(id -> TenantPaths.tenantPlatform(id) + ".operator-apps." + resolvedAppId)
                .orElse("");
        store.upsert(new OperatorAppUiStore.OperatorAppUiRecord(
                resolvedAppId,
                resolvedTitle,
                ownershipMarker,
                objectMapper.writeValueAsString(List.of()),
                null,
                Instant.now()
        ));
        objectTreeService.syncAll();
        return getUi(resolvedAppId, authentication);
    }

    public Map<String, Object> getUi(String appId) throws Exception {
        return getUi(appId, SecurityContextHolder.getContext().getAuthentication());
    }

    public Map<String, Object> getUi(String appId, Authentication authentication) throws Exception {
        String resolvedAppId = resolveTenantAppId(appId, authentication);
        OperatorAppUiStore.OperatorAppUiRecord record = store.findByAppId(resolvedAppId)
                .or(() -> store.findByAppId(appId))
                .orElseThrow(() -> new IllegalArgumentException("Operator app not found: " + appId));
        Optional<String> tenantId = tenantScopeService.resolveTenantId(authentication);
        if (tenantId.isPresent() && !belongsToTenant(record, tenantId.get())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operator app not found: " + appId);
        }
        Map<String, Object> ui = toUiMap(record);
        if (tenantId.isPresent()) {
            collapseUiPaths(ui, tenantId.get());
        }
        return ui;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> saveUi(
            String appId,
            String title,
            String defaultDashboard,
            List<Map<String, String>> dashboards,
            Map<String, Object> alarmBar,
            String agentInstructions
    ) throws Exception {
        return saveUiInternal(appId, title, defaultDashboard, dashboards, alarmBar, agentInstructions, null, null);
    }

    public Map<String, Object> saveUi(
            String appId,
            String title,
            String defaultDashboard,
            List<Map<String, String>> dashboards,
            Map<String, Object> alarmBar,
            String agentInstructions,
            Boolean hideTasksAndEvents,
            Boolean hideDashboardNav
    ) throws Exception {
        return saveUiInternal(
                appId,
                title,
                defaultDashboard,
                dashboards,
                alarmBar,
                agentInstructions,
                hideTasksAndEvents,
                hideDashboardNav
        );
    }

    private Map<String, Object> saveUiInternal(
            String appId,
            String title,
            String defaultDashboard,
            List<Map<String, String>> dashboards,
            Map<String, Object> alarmBar,
            String agentInstructions,
            Boolean hideTasksAndEvents,
            Boolean hideDashboardNav
    ) throws Exception {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (dashboards == null || dashboards.isEmpty()) {
            throw new IllegalArgumentException("dashboards must not be empty");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<String> tenantId = tenantScopeService.resolveTenantId(authentication);
        String resolvedAppId = resolveTenantAppId(appId, authentication);
        if (tenantId.isPresent()) {
            OperatorAppUiStore.OperatorAppUiRecord existing = store.findByAppId(resolvedAppId).orElse(null);
            if (existing != null && !belongsToTenant(existing, tenantId.get())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator app not in tenant scope: " + appId);
            }
            if (existing == null && store.findByAppId(appId).isPresent()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator app not in tenant scope: " + appId);
            }
        }
        List<Map<String, String>> canonicalDashboards = expandDashboardPaths(dashboards, tenantId);
        String resolvedDefault = defaultDashboard;
        if (resolvedDefault == null || resolvedDefault.isBlank()) {
            resolvedDefault = canonicalDashboards.get(0).get("path");
        } else if (tenantId.isPresent()) {
            resolvedDefault = TenantVirtualRoot.toCanonical(resolvedDefault, tenantId.get());
        }
        final String defaultPath = resolvedDefault;
        boolean defaultFound = canonicalDashboards.stream().anyMatch(item -> defaultPath.equals(item.get("path")));
        if (!defaultFound) {
            throw new IllegalArgumentException("defaultDashboard must be one of dashboards[].path");
        }
        String uiExtrasJson = buildUiExtrasJson(
                resolvedAppId,
                alarmBar,
                agentInstructions,
                hideTasksAndEvents,
                hideDashboardNav
        );
        store.upsert(new OperatorAppUiStore.OperatorAppUiRecord(
                resolvedAppId,
                title.trim(),
                defaultPath,
                objectMapper.writeValueAsString(canonicalDashboards),
                uiExtrasJson,
                Instant.now()
        ));
        objectTreeService.syncAll();
        return getUi(resolvedAppId, authentication);
    }

    private String resolveTenantAppId(String appId, Authentication authentication) {
        if (appId == null || appId.isBlank()) {
            return appId;
        }
        Optional<String> tenantId = tenantScopeService.resolveTenantId(authentication);
        if (tenantId.isEmpty()) {
            return appId;
        }
        String prefix = tenantId.get() + "__";
        return appId.startsWith(prefix) ? appId : prefix + appId;
    }

    private boolean belongsToTenant(OperatorAppUiStore.OperatorAppUiRecord record, String tenantId) {
        String prefix = TenantPaths.tenantPlatform(tenantId);
        String appPrefix = tenantId + "__";
        if (record.appId() != null && record.appId().startsWith(appPrefix)) {
            return true;
        }
        if (pathUnder(record.defaultDashboard(), prefix)) {
            return true;
        }
        try {
            List<Map<String, String>> dashboards = objectMapper.readValue(
                    record.dashboardsJson() == null ? "[]" : record.dashboardsJson(),
                    new TypeReference<>() {
                    }
            );
            for (Map<String, String> item : dashboards) {
                if (pathUnder(item.get("path"), prefix)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private static boolean pathUnder(String path, String prefix) {
        return path != null && (path.equals(prefix) || path.startsWith(prefix + "."));
    }

    private List<Map<String, String>> expandDashboardPaths(
            List<Map<String, String>> dashboards,
            Optional<String> tenantId
    ) {
        if (tenantId.isEmpty()) {
            return dashboards;
        }
        List<Map<String, String>> out = new ArrayList<>(dashboards.size());
        for (Map<String, String> item : dashboards) {
            Map<String, String> copy = new LinkedHashMap<>(item);
            String path = item.get("path");
            if (path != null && !path.isBlank()) {
                copy.put("path", TenantVirtualRoot.toCanonical(path, tenantId.get()));
            }
            out.add(copy);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void collapseUiPaths(Map<String, Object> ui, String tenantId) {
        Object defaultDashboard = ui.get("defaultDashboard");
        if (defaultDashboard instanceof String path && !path.isBlank()) {
            ui.put("defaultDashboard", TenantVirtualRoot.toVirtual(path, tenantId));
        }
        Object dashboards = ui.get("dashboards");
        if (dashboards instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, String> row = (Map<String, String>) raw;
                    String path = row.get("path");
                    if (path != null && !path.isBlank()) {
                        row.put("path", TenantVirtualRoot.toVirtual(path, tenantId));
                    }
                }
            }
        }
    }

    public Map<String, Object> saveUi(
            String appId,
            String title,
            String defaultDashboard,
            List<Map<String, String>> dashboards
    ) throws Exception {
        return saveUiInternal(appId, title, defaultDashboard, dashboards, null, null, null, null);
    }

    public Map<String, Object> saveUi(
            String appId,
            String title,
            String defaultDashboard,
            List<Map<String, String>> dashboards,
            Map<String, Object> alarmBar
    ) throws Exception {
        return saveUiInternal(appId, title, defaultDashboard, dashboards, alarmBar, null, null, null);
    }

    public String getAgentInstructions(String appId) {
        try {
            return store.findByAppId(appId)
                    .map(OperatorAppUiStore.OperatorAppUiRecord::uiExtrasJson)
                    .map(this::readAgentInstructions)
                    .orElse("");
        } catch (Exception ex) {
            return "";
        }
    }

    private String readAgentInstructions(String uiExtrasJson) {
        try {
            Map<String, Object> extras = readExtras(uiExtrasJson);
            Object raw = extras.get("agentInstructions");
            return raw != null ? String.valueOf(raw).trim() : "";
        } catch (Exception ex) {
            return "";
        }
    }

    public void saveUiExtras(String appId, Map<String, Object> alarmBar) throws Exception {
        OperatorAppUiStore.OperatorAppUiRecord record = store.findByAppId(appId)
                .orElseThrow(() -> new IllegalArgumentException("Operator app not found: " + appId));
        String uiExtrasJson = buildUiExtrasJson(appId, alarmBar, null, null, null);
        store.upsert(new OperatorAppUiStore.OperatorAppUiRecord(
                record.appId(),
                record.title(),
                record.defaultDashboard(),
                record.dashboardsJson(),
                uiExtrasJson,
                Instant.now()
        ));
    }

    private String buildUiExtrasJson(
            String appId,
            Map<String, Object> alarmBar,
            String agentInstructions,
            Boolean hideTasksAndEvents,
            Boolean hideDashboardNav
    ) throws Exception {
        Map<String, Object> extras = readExtras(store.findByAppId(appId).map(OperatorAppUiStore.OperatorAppUiRecord::uiExtrasJson).orElse(null));
        if (alarmBar != null) {
            if (alarmBar.isEmpty()) {
                extras.remove("alarmBar");
            } else {
                extras.put("alarmBar", alarmBar);
            }
        }
        if (agentInstructions != null) {
            String trimmed = agentInstructions.trim();
            if (trimmed.isEmpty()) {
                extras.remove("agentInstructions");
            } else {
                extras.put("agentInstructions", trimmed);
            }
        }
        putChromeHideFlag(extras, "hideTasksAndEvents", hideTasksAndEvents);
        putChromeHideFlag(extras, "hideDashboardNav", hideDashboardNav);
        if (extras.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(extras);
    }

    private static void putChromeHideFlag(Map<String, Object> extras, String key, Boolean value) {
        if (value == null) {
            return;
        }
        if (value) {
            extras.put(key, true);
        } else {
            extras.remove(key);
        }
    }

    private Map<String, Object> readExtras(String uiExtrasJson) throws Exception {
        if (uiExtrasJson == null || uiExtrasJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(uiExtrasJson, new TypeReference<>() {
        });
    }

    private Map<String, Object> toUiMap(OperatorAppUiStore.OperatorAppUiRecord record) throws Exception {
        List<Map<String, String>> dashboards = objectMapper.readValue(
                record.dashboardsJson(),
                new TypeReference<>() {
                }
        );
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("appId", record.appId());
        ui.put("title", record.title());
        ui.put("defaultDashboard", record.defaultDashboard());
        ui.put("dashboards", dashboards);
        Map<String, Object> extras = readExtras(record.uiExtrasJson());
        Object alarmBar = extras.get("alarmBar");
        if (alarmBar != null) {
            ui.put("alarmBar", alarmBar);
        }
        Object reports = extras.get("reports");
        if (reports != null) {
            ui.put("reports", reports);
        }
        Object defaultReport = extras.get("defaultReport");
        if (defaultReport != null) {
            ui.put("defaultReport", defaultReport);
        }
        Object agentInstructions = extras.get("agentInstructions");
        if (agentInstructions != null) {
            ui.put("agentInstructions", agentInstructions);
        }
        if (Boolean.TRUE.equals(extras.get("hideTasksAndEvents"))) {
            ui.put("hideTasksAndEvents", true);
        }
        if (Boolean.TRUE.equals(extras.get("hideDashboardNav"))) {
            ui.put("hideDashboardNav", true);
        }
        return ui;
    }
}
