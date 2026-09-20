package com.ispf.server.application.bundle;

import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleDashboard;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleManifest;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleReport;
import com.ispf.server.operator.OperatorAppObjectTreeService;
import com.ispf.server.operator.OperatorAppUiService;
import com.ispf.server.operator.OperatorAppUiStore;
import com.ispf.server.report.ReportService;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the Operator UI descriptor of an application bundle ({@code operatorUi} /
 * {@code operatorManifest} / fallback from dashboards + reports) and persists it as the
 * operator-app record. Pure resolution helpers are static so they can be reused by the
 * visual-group sync and unit-tested without a container.
 */
@Component
public class BundleOperatorUiSync {

    private final OperatorAppUiStore operatorAppUiStore;
    private final OperatorAppObjectTreeService operatorAppObjectTreeService;
    private final ObjectMapper objectMapper;

    public BundleOperatorUiSync(
            OperatorAppUiStore operatorAppUiStore,
            OperatorAppObjectTreeService operatorAppObjectTreeService,
            ObjectMapper objectMapper
    ) {
        this.operatorAppUiStore = operatorAppUiStore;
        this.operatorAppObjectTreeService = operatorAppObjectTreeService;
        this.objectMapper = objectMapper;
    }

    /** Upserts the operator-app record from the bundle; no-op when the bundle has no operator-facing UI. */
    public void sync(String appId, BundleManifest manifest) throws Exception {
        if (!hasOperatorUiManifest(manifest)) {
            return;
        }
        Map<String, Object> ui = resolveOperatorUiForSync(appId, manifest);
        String title = ui.get("title") != null ? String.valueOf(ui.get("title")) : appId;
        String defaultDashboard = ui.get("defaultDashboard") != null
                ? String.valueOf(ui.get("defaultDashboard"))
                : "";
        List<?> dashboardEntries = ui.get("dashboards") instanceof List<?> list ? list : List.of();
        List<?> reportEntries = ui.get("reports") instanceof List<?> list ? list : List.of();
        boolean hasDashboards = !dashboardEntries.isEmpty();
        boolean hasReports = !reportEntries.isEmpty();
        // Hollow operatorUi { dashboards: [] } used to skip sync and leave Operator on "UI not found".
        if (!hasDashboards && !hasReports) {
            return;
        }
        List<Map<String, String>> dashboards = new ArrayList<>();
        if (hasDashboards) {
            for (Object rawEntry : dashboardEntries) {
                Map<String, String> item = normalizeDashboardEntry(rawEntry);
                if (item != null) {
                    dashboards.add(item);
                }
            }
            if (dashboards.isEmpty() && !hasReports) {
                return;
            }
            if (!dashboards.isEmpty() && defaultDashboard.isBlank()) {
                defaultDashboard = dashboards.get(0).get("path");
            }
        }
        Map<String, Object> alarmBar = null;
        if (ui.get("alarmBar") instanceof Map<?, ?> alarmBarMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) alarmBarMap;
            alarmBar = typed;
        }
        // Preserve prior extras (e.g. manually set bridge URL), then overlay bundle fields.
        Map<String, Object> extras = new LinkedHashMap<>();
        operatorAppUiStore.findByAppId(appId).ifPresent(existing -> {
            try {
                if (existing.uiExtrasJson() != null && !existing.uiExtrasJson().isBlank()) {
                    extras.putAll(objectMapper.readValue(existing.uiExtrasJson(), new TypeReference<>() {
                    }));
                }
            } catch (Exception ignored) {
                // Corrupt extras must not block operatorUi sync.
            }
        });
        if (alarmBar != null && !alarmBar.isEmpty()) {
            extras.put("alarmBar", alarmBar);
        }
        if (hasReports) {
            List<Map<String, String>> reports = new ArrayList<>();
            for (Object rawEntry : reportEntries) {
                Map<String, String> item = normalizeDashboardEntry(rawEntry);
                if (item != null) {
                    reports.add(item);
                }
            }
            if (!reports.isEmpty()) {
                extras.put("reports", reports);
                if (ui.get("defaultReport") != null) {
                    extras.put("defaultReport", String.valueOf(ui.get("defaultReport")));
                } else {
                    extras.put("defaultReport", reports.get(0).get("path"));
                }
            }
        }
        if (Boolean.TRUE.equals(ui.get("hideTasksAndEvents"))) {
            extras.put("hideTasksAndEvents", true);
        }
        if (Boolean.TRUE.equals(ui.get("hideDashboardNav"))) {
            extras.put("hideDashboardNav", true);
        }
        // ADR-0054 launch contract: persist so Open app UI / spaNav survive deploy.
        putOperatorLaunchExtra(ui, extras, "externalSpaUrl");
        putOperatorLaunchExtra(ui, extras, "spaNav");
        putOperatorLaunchExtra(ui, extras, "uiPack");
        putOperatorLaunchExtra(ui, extras, "eventJournalObjectPath");
        String uiExtrasJson = extras.isEmpty() ? null : objectMapper.writeValueAsString(extras);
        operatorAppUiStore.upsert(new OperatorAppUiStore.OperatorAppUiRecord(
                appId,
                title,
                defaultDashboard,
                objectMapper.writeValueAsString(dashboards),
                uiExtrasJson,
                Instant.now()
        ));
        operatorAppObjectTreeService.syncAll();
    }

    /**
     * Operator UI descriptor served to the console for an active bundle: explicit {@code operatorUi}
     * wins, otherwise it is derived from dashboards / reports.
     *
     * @throws IllegalArgumentException when the bundle defines neither
     */
    public static Map<String, Object> resolveOperatorUi(String appId, BundleManifest manifest) {
        if (manifest.operatorUi() != null && !manifest.operatorUi().isEmpty()) {
            return manifest.operatorUi();
        }
        if ((manifest.dashboards() != null && !manifest.dashboards().isEmpty())
                || (manifest.reports() != null && !manifest.reports().isEmpty())) {
            return buildOperatorUiFromBundle(appId, manifest);
        }
        throw new IllegalArgumentException(
                "Operator UI not defined: set operatorUi or dashboards[] in bundle for app: " + appId
        );
    }

    public static boolean hasOperatorUiManifest(BundleManifest manifest) {
        // A non-empty operatorUi map with dashboards:[] is NOT operator-ready (catalog stubs).
        return hasUsableOperatorDashboards(manifest.operatorUi())
                || hasUsableOperatorReports(manifest.operatorUi())
                || hasUsableOperatorDashboards(manifest.operatorManifest())
                || hasUsableOperatorReports(manifest.operatorManifest())
                || (manifest.dashboards() != null && !manifest.dashboards().isEmpty())
                || (manifest.reports() != null && !manifest.reports().isEmpty());
    }

    /** Copy ADR-0054 / spa launch fields from bundle operatorUi into persisted extras. */
    static void putOperatorLaunchExtra(Map<String, Object> operatorUi, Map<String, Object> extras, String key) {
        if (operatorUi == null || extras == null || key == null) {
            return;
        }
        Object value = operatorUi.get(key);
        if (value == null) {
            return;
        }
        if ("externalSpaUrl".equals(key)) {
            if (!(value instanceof String spa) || !OperatorAppUiService.isSafeOperatorLaunchUrl(spa)) {
                return;
            }
            extras.put(key, spa.trim());
            return;
        }
        extras.put(key, value);
    }

    /**
     * Prefer a usable operatorUi / operatorManifest; never keep an empty dashboards[] shell
     * when the bundle already defines dashboards, reports, or operatorManifest screens.
     */
    private static Map<String, Object> resolveOperatorUiForSync(String appId, BundleManifest manifest) {
        if (hasUsableOperatorDashboards(manifest.operatorUi())) {
            return manifest.operatorUi();
        }
        if (hasUsableOperatorDashboards(manifest.operatorManifest())
                || hasUsableOperatorReports(manifest.operatorManifest())) {
            return normalizeOperatorManifestUi(appId, manifest);
        }
        return buildOperatorUiFromBundle(appId, manifest);
    }

    private static Map<String, Object> normalizeOperatorManifestUi(String appId, BundleManifest manifest) {
        Map<String, Object> source = manifest.operatorManifest() != null
                ? new LinkedHashMap<>(manifest.operatorManifest())
                : new LinkedHashMap<>();
        if (hasUsableOperatorDashboards(source) || hasUsableOperatorReports(source)) {
            if (!source.containsKey("appId")) {
                source.put("appId", appId);
            }
            if (!source.containsKey("title")) {
                source.put("title", manifest.displayName() != null ? manifest.displayName() : appId);
            }
            return source;
        }
        return buildOperatorUiFromBundle(appId, manifest);
    }

    private static boolean hasUsableOperatorDashboards(Map<String, Object> ui) {
        if (ui == null || ui.isEmpty()) {
            return false;
        }
        Object dashboards = ui.get("dashboards");
        return dashboards instanceof List<?> list && !list.isEmpty();
    }

    private static boolean hasUsableOperatorReports(Map<String, Object> ui) {
        if (ui == null || ui.isEmpty()) {
            return false;
        }
        Object reports = ui.get("reports");
        return reports instanceof List<?> list && !list.isEmpty();
    }

    /** Accept `{path,title}` maps or bare path strings (legacy tank-farm style). */
    private static Map<String, String> normalizeDashboardEntry(Object rawEntry) {
        if (rawEntry == null) {
            return null;
        }
        if (rawEntry instanceof String path && !path.isBlank()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("path", path.trim());
            item.put("title", path.trim());
            return item;
        }
        if (rawEntry instanceof Map<?, ?> map) {
            Object path = map.get("path");
            if (path == null || String.valueOf(path).isBlank()) {
                return null;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("path", String.valueOf(path));
            Object title = map.get("title");
            item.put("title", title != null ? String.valueOf(title) : String.valueOf(path));
            return item;
        }
        return null;
    }

    private static Map<String, Object> buildOperatorUiFromBundle(String appId, BundleManifest manifest) {
        List<Map<String, Object>> dashboards = new ArrayList<>();
        String defaultDashboard = null;
        if (manifest.dashboards() != null) {
            for (BundleDashboard dashboard : manifest.dashboards()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("path", dashboard.path());
                entry.put("title", dashboard.title() != null ? dashboard.title() : dashboard.path());
                dashboards.add(entry);
                if (defaultDashboard == null) {
                    defaultDashboard = dashboard.path();
                }
            }
        }

        List<Map<String, Object>> reports = new ArrayList<>();
        String defaultReport = null;
        if (manifest.reports() != null) {
            for (BundleReport report : manifest.reports()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                String path = ReportService.reportPath(report.reportId());
                entry.put("path", path);
                entry.put("title", report.title() != null ? report.title() : report.reportId());
                reports.add(entry);
                if (defaultReport == null) {
                    defaultReport = path;
                }
            }
        }

        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("appId", appId);
        ui.put("title", manifest.displayName() != null ? manifest.displayName() : appId);
        ui.put("defaultDashboard", defaultDashboard != null ? defaultDashboard : "");
        ui.put("dashboards", dashboards);
        if (!reports.isEmpty()) {
            ui.put("reports", reports);
            ui.put("defaultReport", defaultReport);
        }
        if (manifest.operatorManifest() != null && manifest.operatorManifest().get("alarmBar") != null) {
            ui.put("alarmBar", manifest.operatorManifest().get("alarmBar"));
        }
        return ui;
    }
}
