package com.ispf.server.ai.context;

import com.ispf.driver.DriverMetadata;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.config.AiProperties;
import com.ispf.server.ai.agent.AgentDashboardGuide;
import com.ispf.server.ai.agent.AgentWidgetCatalog;
import com.ispf.server.driver.DriverCatalog;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.update.PlatformVersionSupport;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compact platform knowledge injected into the tree-first agent system prompt (FW-45).
 */
@Service
public class PlatformBriefingService {

    private static final int LIVE_CHILDREN_CAP = 30;
    private static final int FOLDER_CHILDREN_CAP = 12;
    private static final String PLATFORM_ROOT = "root.platform";

    private static final Set<ObjectType> LIVE_OBJECT_COUNT_TYPES = Set.of(
            ObjectType.DEVICE,
            ObjectType.DASHBOARD,
            ObjectType.MIMIC,
            ObjectType.WORKFLOW,
            ObjectType.ALERT,
            ObjectType.CORRELATOR,
            ObjectType.CUSTOM,
            ObjectType.FUNCTION,
            ObjectType.REPORT,
            ObjectType.WORK_ORDER,
            ObjectType.OPERATION,
            ObjectType.LOT,
            ObjectType.APPLICATION
    );

    private static final List<Map<String, String>> FEATURE_INDEX = List.of(
            feature("bundles", "Bundle deploy", "manifest JSON, migrations, functions, dashboards", "bundle deploy import"),
            feature("bff", "BFF invoke", "POST /api/v1/bff/invoke application functions", "bff invoke function"),
            feature("workflows", "BPMN workflows", "workflows[] in bundle, publish_nats, fire_event tasks", "workflow bpmn"),
            feature("correlators", "Event correlators & alerts", "configure_alert, configure_correlator, list_automation", "correlator alert automation"),
            feature("operator-ui", "Operator HMI", "configure_operator_ui defaultDashboard dashboards[]", "operator hmi default dashboard"),
            feature("federation", "Federation bind", "overlay remote peer on local path", "federation peer bind"),
            feature("dashboards", "Dashboards", "DASHBOARD objects, layout.widgets[], widget types", "dashboard widget layout"),
            feature("events", "Event catalog", "events[] in bundle, WS subscribe_events", "event catalog subscribe"),
            feature("virtual", "Virtual driver", "profiles demo, meter, weighbridge, rack-signals, lab, unified", "virtual profile meter"),
            feature("history", "Variable history", "historyEnabled, JDBC batch store, charts, export CSV", "variable history trend historian"),
            feature("mqtt-gateway", "MQTT gateway", "mqtt-gateway-v1, lastIngress, dispatchTelemetry, ingressTopicLanes", "mqtt gateway orchestrator ingress"),
            feature("telemetry", "Telemetry pipeline", "TELEMETRY_ONLY, coalesce, dual-lane bus, loadtest", "telemetry coalesce historian throughput"),
            feature("ai-studio", "AI Studio", "tree-first agent, validate_bundle, import_package", "ai agent studio")
    );

    private static final List<Map<String, String>> VIRTUAL_PROFILES = List.of(
            Map.of("driverId", "virtual", "vars", "temperature, waves, meter, geo, tables, binary, status", "use", "OOTB multi-type simulator")
    );

    private final AiProperties aiProperties;
    private final ContextPackService contextPackService;
    private final DriverCatalog driverCatalog;
    private final ApplicationDataStore applicationDataStore;
    private final ApplicationBundleSnapshotStore bundleSnapshotStore;
    private final ObjectManager objectManager;
    private final CacheManager cacheManager;
    private final PlatformBriefingCacheEpoch briefingCacheEpoch;
    private final Optional<BuildProperties> buildProperties;

    public PlatformBriefingService(
            AiProperties aiProperties,
            ContextPackService contextPackService,
            DriverCatalog driverCatalog,
            ApplicationDataStore applicationDataStore,
            ApplicationBundleSnapshotStore bundleSnapshotStore,
            ObjectManager objectManager,
            CacheManager cacheManager,
            PlatformBriefingCacheEpoch briefingCacheEpoch,
            Optional<BuildProperties> buildProperties
    ) {
        this.aiProperties = aiProperties;
        this.contextPackService = contextPackService;
        this.driverCatalog = driverCatalog;
        this.applicationDataStore = applicationDataStore;
        this.bundleSnapshotStore = bundleSnapshotStore;
        this.objectManager = objectManager;
        this.cacheManager = cacheManager;
        this.briefingCacheEpoch = briefingCacheEpoch;
        this.buildProperties = buildProperties;
    }

    public String buildBriefing(String rootPath, boolean includeStaticKnowledge) {
        String cacheKey = briefingCacheKey(rootPath, includeStaticKnowledge);
        Cache cache = cacheManager.getCache("platformBriefing");
        if (cache != null) {
            String cached = cache.get(cacheKey, String.class);
            if (cached != null) {
                return cached;
            }
            String built = buildBriefingUncached(rootPath, includeStaticKnowledge);
            cache.put(cacheKey, built);
            return built;
        }
        return buildBriefingUncached(rootPath, includeStaticKnowledge);
    }

    private String briefingCacheKey(String rootPath, boolean includeStaticKnowledge) {
        String effectiveRoot = rootPath == null || rootPath.isBlank() ? "root" : rootPath.trim();
        return briefingCacheEpoch.current() + ":" + effectiveRoot + ":" + includeStaticKnowledge;
    }

    private String buildBriefingUncached(String rootPath, boolean includeStaticKnowledge) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context pack: ").append(contextPackService.contextPackVersion()).append('\n');
        if (includeStaticKnowledge) {
            appendDrivers(sb);
            appendVirtualProfiles(sb);
            appendWidgetCatalog(sb);
            appendExamples(sb);
            appendFeatures(sb);
        }
        appendLiveSnapshot(sb, rootPath);
        return truncate(sb.toString(), aiProperties.getBriefingMaxChars());
    }

    private void appendDrivers(StringBuilder sb) {
        sb.append("\n### Drivers (driverId | name | maturity)\n");
        for (DriverMetadata driver : driverCatalog.list()) {
            sb.append("- ")
                    .append(driver.id())
                    .append(" | ")
                    .append(driver.name())
                    .append(" | ")
                    .append(driver.maturity().name())
                    .append('\n');
        }
        sb.append("Use list_drivers or get_driver_help for config templates.\n");
    }

    private void appendVirtualProfiles(StringBuilder sb) {
        sb.append("\n### Virtual driver (no profiles)\n");
        for (Map<String, String> profile : VIRTUAL_PROFILES) {
            sb.append("- ")
                    .append(profile.getOrDefault("driverId", "virtual"))
                    .append(": ")
                    .append(profile.get("vars"))
                    .append(" — ")
                    .append(profile.get("use"))
                    .append('\n');
        }
        sb.append("Domain plants: relative blueprints (list_relative_blueprints), not driver profiles.\n");
    }

    private void appendWidgetCatalog(StringBuilder sb) {
        sb.append("\n### Dashboard widgets\n");
        sb.append("Use get_widget_catalog or get_automation_schema topic=dashboard for all ")
                .append(AgentWidgetCatalog.all().size())
                .append(" widget types, bindings, and layout templates.\n");
        @SuppressWarnings("unchecked")
        List<String> workflow = (List<String>) AgentDashboardGuide.summary().get("workflow");
        for (String step : workflow) {
            sb.append("- ").append(step).append('\n');
        }
        sb.append("Layout variable: layout (never widgets). Drill-down: object-table selectionKey + rowTargetDashboard.\n");
    }

    @SuppressWarnings("unchecked")
    private void appendExamples(StringBuilder sb) {
        sb.append("\n### Reference examples\n");
        Map<String, Object> pack = contextPackService.loadPack();
        Object summaries = pack.get("exampleSummaries");
        if (summaries instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    sb.append("- ")
                            .append(row.get("appId"))
                            .append(" | ")
                            .append(String.valueOf(row.get("purpose")))
                            .append(" | sections: ")
                            .append(String.valueOf(row.get("keySections")))
                            .append('\n');
                }
            }
            return;
        }
        Object examples = pack.get("examples");
        if (examples instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> example) {
                    String appId = String.valueOf(
                            example.get("packageId") != null ? example.get("packageId") : example.get("appId")
                    );
                    Object sections = example.get("sections");
                    sb.append("- ")
                            .append(appId)
                            .append(" | sections: ")
                            .append(sections)
                            .append('\n');
                }
            }
        }
        sb.append("Use list_examples or get_example_bundle for manifest details.\n");
    }

    private void appendFeatures(StringBuilder sb) {
        sb.append("\n### Platform features\n");
        Object fromPack = contextPackService.loadPack().get("featureIndex");
        if (fromPack instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    sb.append("- ")
                            .append(row.get("title"))
                            .append(": ")
                            .append(row.get("description"))
                            .append('\n');
                }
            }
            return;
        }
        for (Map<String, String> feature : FEATURE_INDEX) {
            sb.append("- ")
                    .append(feature.get("title"))
                    .append(": ")
                    .append(feature.get("description"))
                    .append('\n');
        }
    }

    private void appendLiveSnapshot(StringBuilder sb, String rootPath) {
        sb.append("\n### Live instance snapshot\n");
        appendServerAndPackVersions(sb);
        appendTopReadinessGaps(sb);
        List<Map<String, Object>> apps = new ArrayList<>();
        for (Map<String, Object> app : applicationDataStore.listAllApps()) {
            String appId = String.valueOf(app.get("app_id"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("appId", appId);
            row.put("displayName", app.get("display_name"));
            Optional<ApplicationBundleSnapshotStore.BundleSnapshot> active = bundleSnapshotStore.findActive(appId);
            active.ifPresent(snapshot -> row.put("bundleVersion", snapshot.bundleVersion()));
            apps.add(row);
        }
        if (apps.isEmpty()) {
            sb.append("Deployed applications: (none registered)\n");
        } else {
            sb.append("Deployed applications:\n");
            for (Map<String, Object> app : apps) {
                sb.append("- ")
                        .append(app.get("appId"));
                if (app.containsKey("bundleVersion")) {
                    sb.append(" v").append(app.get("bundleVersion"));
                }
                sb.append('\n');
            }
        }

        appendObjectTypeCounts(sb);
        String effectiveRoot = rootPath == null || rootPath.isBlank() ? "root" : rootPath.trim();
        appendDirectChildren(sb, effectiveRoot, LIVE_CHILDREN_CAP,
                "Tree under " + effectiveRoot + " (direct children, max " + LIVE_CHILDREN_CAP + ")");
        appendPlatformTreeDetail(sb);
    }

    private void appendServerAndPackVersions(StringBuilder sb) {
        String serverVersion = PlatformVersionSupport.currentVersion(buildProperties);
        String packVersion = contextPackService.contextPackVersion();
        sb.append("Server version: ").append(serverVersion).append('\n');
        String packNumeric = packVersion.startsWith("ispf-")
                ? packVersion.substring("ispf-".length())
                : packVersion;
        if (PlatformVersionSupport.compare(packNumeric, serverVersion) < 0) {
            sb.append("WARNING: context pack (")
                    .append(packVersion)
                    .append(") is older than server — use list_objects/list_applications for live state, ")
                    .append("not search_context alone.\n");
        }
    }

    private void appendTopReadinessGaps(StringBuilder sb) {
        List<Map<String, Object>> gaps = ContextPackService.topGaps(
                ContextPackService.competitiveGaps(contextPackService.loadPack()),
                5
        );
        if (gaps.isEmpty()) {
            return;
        }
        sb.append("Top readiness gaps (search_context topic=gaps):\n");
        for (Map<String, Object> gap : gaps) {
            sb.append("- ")
                    .append(gap.get("dimension"))
                    .append(" gap=")
                    .append(gap.get("gap"))
                    .append(" (current=")
                    .append(gap.get("current"))
                    .append(" target=")
                    .append(gap.get("target"))
                    .append(")\n");
        }
    }

    private void appendObjectTypeCounts(StringBuilder sb) {
        sb.append("Live object counts:\n");
        try {
            Map<ObjectType, Integer> counts = new EnumMap<>(ObjectType.class);
            for (PlatformObject object : objectManager.tree().all()) {
                if (LIVE_OBJECT_COUNT_TYPES.contains(object.type())) {
                    counts.merge(object.type(), 1, Integer::sum);
                }
            }
            if (counts.isEmpty()) {
                sb.append("- (none)\n");
                return;
            }
            Map<ObjectType, Integer> sorted = new TreeMap<>(counts);
            for (Map.Entry<ObjectType, Integer> entry : sorted.entrySet()) {
                sb.append("- ")
                        .append(entry.getKey().name())
                        .append(": ")
                        .append(entry.getValue())
                        .append('\n');
            }
        } catch (Exception ex) {
            sb.append("- (unable to count: ").append(ex.getMessage()).append(")\n");
        }
    }

    private void appendDirectChildren(StringBuilder sb, String parentPath, int cap, String heading) {
        sb.append(heading).append(":\n");
        try {
            List<PlatformObject> children = objectManager.tree().childrenOf(parentPath);
            int count = 0;
            for (PlatformObject child : children) {
                if (count >= cap) {
                    sb.append("- ... (").append(children.size() - cap).append(" more)\n");
                    break;
                }
                sb.append("- ")
                        .append(child.path())
                        .append(" [")
                        .append(child.type())
                        .append("]\n");
                count++;
            }
            if (count == 0) {
                sb.append("- (no children)\n");
            }
        } catch (Exception ex) {
            sb.append("- (unable to list: ").append(ex.getMessage()).append(")\n");
        }
    }

    private void appendPlatformTreeDetail(StringBuilder sb) {
        sb.append("Platform tree under ").append(PLATFORM_ROOT).append(":\n");
        try {
            if (objectManager.tree().findByPath(PLATFORM_ROOT).isEmpty()) {
                sb.append("- (root.platform not found)\n");
                return;
            }
            List<PlatformObject> folders = objectManager.tree().childrenOf(PLATFORM_ROOT);
            if (folders.isEmpty()) {
                sb.append("- (no platform folders)\n");
                return;
            }
            for (PlatformObject folder : folders) {
                List<PlatformObject> items = objectManager.tree().childrenOf(folder.path());
                sb.append("- ")
                        .append(folder.path())
                        .append(" [")
                        .append(folder.type())
                        .append("] — ")
                        .append(items.size())
                        .append(" children\n");
                int shown = 0;
                for (PlatformObject item : items) {
                    if (shown >= FOLDER_CHILDREN_CAP) {
                        sb.append("  - ... (")
                                .append(items.size() - shown)
                                .append(" more)\n");
                        break;
                    }
                    sb.append("  - ")
                            .append(item.path())
                            .append(" [")
                            .append(item.type())
                            .append("]\n");
                    shown++;
                }
            }
        } catch (Exception ex) {
            sb.append("- (unable to list platform tree: ").append(ex.getMessage()).append(")\n");
        }
    }

    private static Map<String, String> feature(String id, String title, String description, String keywords) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("title", title);
        row.put("description", description);
        row.put("keywords", keywords);
        return row;
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars - 20) + "\n… (truncated)";
    }
}
