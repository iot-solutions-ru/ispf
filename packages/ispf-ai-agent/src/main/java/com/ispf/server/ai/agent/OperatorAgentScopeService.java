package com.ispf.server.ai.agent;

import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.operator.OperatorAppUiService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OperatorAgentScopeService {

    private final OperatorAppUiService operatorAppUiService;
    private final ApplicationBundleDeployService bundleDeployService;
    private final ObjectMapper objectMapper;

    public OperatorAgentScopeService(
            OperatorAppUiService operatorAppUiService,
            ApplicationBundleDeployService bundleDeployService,
            ObjectMapper objectMapper
    ) {
        this.operatorAppUiService = operatorAppUiService;
        this.bundleDeployService = bundleDeployService;
        this.objectMapper = objectMapper;
    }

    public OperatorAgentScope resolve(String appId) throws Exception {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId is required");
        }
        String normalizedAppId = appId.trim();
        Set<String> prefixes = new LinkedHashSet<>();
        String title = normalizedAppId;

        try {
            Map<String, Object> ui = operatorAppUiService.getUi(normalizedAppId);
            title = stringOr(ui.get("title"), normalizedAppId);
            collectFromUi(ui, normalizedAppId, prefixes);
        } catch (Exception ignored) {
            // registry miss — try bundle UI
        }

        if (prefixes.isEmpty()) {
            try {
                Map<String, Object> ui = bundleDeployService.operatorUi(normalizedAppId);
                title = stringOr(ui.get("title"), normalizedAppId);
                collectFromUi(ui, normalizedAppId, prefixes);
            } catch (Exception ignored) {
                // no bundle UI
            }
        }

        try {
            Map<String, Object> manifest = bundleDeployService.operatorManifest(normalizedAppId);
            title = stringOr(manifest.get("title"), title);
            collectFromManifest(manifest, prefixes);
        } catch (Exception ignored) {
            // manifest optional
        }

        for (String reportPath : bundleDeployService.activeReportPaths(normalizedAppId)) {
            addPath(prefixes, reportPath);
        }

        prefixes.add(ApplicationBundleDeployService.applicationTreePath(normalizedAppId));
        prefixes.add(ApplicationBundleDeployService.operatorAppTreePath(normalizedAppId));

        if (prefixes.isEmpty()) {
            throw new IllegalArgumentException("Operator app has no scoped paths: " + normalizedAppId);
        }

        String briefingRoot = prefixes.iterator().next();
        return new OperatorAgentScope(normalizedAppId, title, List.copyOf(prefixes), briefingRoot);
    }

    @SuppressWarnings("unchecked")
    private void collectFromUi(Map<String, Object> ui, String appId, Set<String> prefixes) {
        Object dashboards = ui.get("dashboards");
        if (dashboards instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    addPath(prefixes, stringOr(map.get("path"), null));
                }
            }
        }
        Object reports = ui.get("reports");
        if (reports instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    addPath(prefixes, stringOr(map.get("path"), null));
                }
            }
        }
        addPath(prefixes, stringOr(ui.get("eventJournalObjectPath"), null));
        addPath(prefixes, stringOr(ui.get("workQueueWorkflowPathPrefix"), null));
        if (appId.equals("platform")) {
            addPath(prefixes, "root.platform.workflows");
        }
        Object alarmBar = ui.get("alarmBar");
        if (alarmBar instanceof Map<?, ?> bar) {
            Object rules = bar.get("rules");
            if (rules instanceof List<?> ruleList) {
                for (Object rule : ruleList) {
                    if (rule instanceof Map<?, ?> map) {
                        addPath(prefixes, stringOr(map.get("objectPathPrefix"), null));
                    }
                }
            }
        }
        String defaultDashboard = stringOr(ui.get("defaultDashboard"), null);
        addPath(prefixes, defaultDashboard);
    }

    @SuppressWarnings("unchecked")
    private void collectFromManifest(Map<String, Object> manifest, Set<String> prefixes) {
        Object screens = manifest.get("screens");
        if (!(screens instanceof List<?> screenList)) {
            return;
        }
        for (Object screen : screenList) {
            if (!(screen instanceof Map<?, ?> map)) {
                continue;
            }
            addPath(prefixes, stringOr(map.get("objectPath"), null));
            addPath(prefixes, stringOr(map.get("parentPath"), null));
            addPath(prefixes, stringOr(map.get("dashboardPath"), null));
            Object actions = map.get("actions");
            if (actions instanceof List<?> actionList) {
                for (Object action : actionList) {
                    if (action instanceof Map<?, ?> actionMap) {
                        addPath(prefixes, stringOr(actionMap.get("objectPath"), null));
                    }
                }
            }
            Object table = map.get("table");
            if (table instanceof Map<?, ?> tableMap) {
                addPath(prefixes, stringOr(tableMap.get("objectPath"), null));
                addPath(prefixes, stringOr(tableMap.get("parentPath"), null));
            }
        }
        Object alarmBar = manifest.get("alarmBar");
        if (alarmBar instanceof Map<?, ?> bar) {
            Object rules = bar.get("rules");
            if (rules instanceof List<?> ruleList) {
                for (Object rule : ruleList) {
                    if (rule instanceof Map<?, ?> ruleMap) {
                        addPath(prefixes, stringOr(ruleMap.get("objectPathPrefix"), null));
                    }
                }
            }
        }
    }

    private static void addPath(Set<String> prefixes, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        prefixes.add(path.trim());
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            prefixes.add(path.substring(0, lastDot));
        }
    }

    private static String stringOr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
