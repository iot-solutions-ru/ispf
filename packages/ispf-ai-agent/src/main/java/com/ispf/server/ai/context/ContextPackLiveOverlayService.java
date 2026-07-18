package com.ispf.server.ai.context;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DriverMetadata;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.cache.PlatformBriefingCacheEpoch;
import com.ispf.server.driver.DriverCatalog;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.platform.update.PlatformVersionSupport;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * BL-182: live platform slice merged into ContextPack info (auto-refresh via cache epoch).
 */
@Service
public class ContextPackLiveOverlayService {

    private static final Set<ObjectType> COUNT_TYPES = Set.of(
            ObjectType.DEVICE,
            ObjectType.DASHBOARD,
            ObjectType.MIMIC,
            ObjectType.WORKFLOW,
            ObjectType.ALERT,
            ObjectType.CORRELATOR,
            ObjectType.CUSTOM,
            ObjectType.FUNCTION,
            ObjectType.REPORT,
            ObjectType.APPLICATION
    );

    private final DriverCatalog driverCatalog;
    private final ApplicationDataStore applicationDataStore;
    private final ApplicationBundleSnapshotStore bundleSnapshotStore;
    private final ObjectTreePort ObjectTreePort;
    private final PlatformBriefingCacheEpoch briefingCacheEpoch;
    private final Optional<BuildProperties> buildProperties;

    public ContextPackLiveOverlayService(
            DriverCatalog driverCatalog,
            ApplicationDataStore applicationDataStore,
            ApplicationBundleSnapshotStore bundleSnapshotStore,
            ObjectTreePort ObjectTreePort,
            PlatformBriefingCacheEpoch briefingCacheEpoch,
            Optional<BuildProperties> buildProperties
    ) {
        this.driverCatalog = driverCatalog;
        this.applicationDataStore = applicationDataStore;
        this.bundleSnapshotStore = bundleSnapshotStore;
        this.ObjectTreePort = ObjectTreePort;
        this.briefingCacheEpoch = briefingCacheEpoch;
        this.buildProperties = buildProperties;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> live = new LinkedHashMap<>();
        live.put("refreshedAt", Instant.now().toString());
        live.put("cacheEpoch", briefingCacheEpoch.current());
        live.put("serverVersion", PlatformVersionSupport.currentVersion(buildProperties));

        List<String> driverIds = new ArrayList<>();
        for (DriverMetadata driver : driverCatalog.list()) {
            driverIds.add(driver.id());
        }
        live.put("driverCount", driverIds.size());
        live.put("driverIds", driverIds);

        List<Map<String, Object>> apps = new ArrayList<>();
        for (Map<String, Object> app : applicationDataStore.listAllApps()) {
            String appId = String.valueOf(app.get("app_id"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("appId", appId);
            row.put("displayName", app.get("display_name"));
            bundleSnapshotStore.findActive(appId)
                    .ifPresent(snapshot -> row.put("bundleVersion", snapshot.bundleVersion()));
            apps.add(row);
        }
        live.put("applicationCount", apps.size());
        live.put("applications", apps);

        Map<String, Integer> objectCounts = new LinkedHashMap<>();
        try {
            Map<ObjectType, Integer> counts = new EnumMap<>(ObjectType.class);
            for (PlatformObject object : ObjectTreePort.tree().all()) {
                if (COUNT_TYPES.contains(object.type())) {
                    counts.merge(object.type(), 1, Integer::sum);
                }
            }
            for (Map.Entry<ObjectType, Integer> entry : new TreeMap<>(counts).entrySet()) {
                objectCounts.put(entry.getKey().name(), entry.getValue());
            }
        } catch (Exception ex) {
            live.put("objectCountError", ex.getMessage());
        }
        live.put("objectCounts", objectCounts);
        return live;
    }
}
