package com.ispf.server.bootstrap;

import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.eventfilter.EventFilterObjectService;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.migration.MigrationObjectService;
import com.ispf.server.process.ProcessProgramPaths;
import com.ispf.server.query.ObjectQueryCatalog;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.security.PlatformUserService;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Default sibling order for platform system catalog folders under {@code root.platform}.
 * Matches the production VPS layout (Security first, then Devices, automation, models, SQL catalogs, …).
 */
public final class PlatformCatalogSortOrder {

    public static final String ROOT = "root";
    public static final String PLATFORM_ROOT = "root.platform";

    private static final List<String> ROOT_CHILDREN = List.of(
            PLATFORM_ROOT,
            "root.tenant"
    );

    /** Canonical order of direct children under {@code root.platform}. */
    private static final List<String> PLATFORM_CHILDREN = List.of(
            PlatformUserService.SECURITY_ROOT,
            "root.platform.devices",
            "root.platform.alert-rules",
            "root.platform.operator-apps",
            "root.platform.dashboards",
            "root.platform.mimics",
            "root.platform.relative-blueprints",
            "root.platform.absolute-blueprints",
            "root.platform.instance-types",
            "root.platform.reports",
            "root.platform.correlators",
            "root.platform.workflows",
            ObjectQueryCatalog.QUERIES_ROOT,
            EventFilterObjectService.EVENT_FILTERS_ROOT,
            ProcessProgramPaths.PROCESS_PROGRAMS_ROOT,
            "root.platform.mes",
            ScheduleObjectService.SCHEDULES_ROOT,
            DataSourcePathResolver.DATA_SOURCES_ROOT,
            SqlBindingObjectService.BINDINGS_ROOT,
            MigrationObjectService.MIGRATIONS_ROOT,
            FederationPaths.FEDERATION_ROOT,
            "root.platform.applications",
            "root.platform.instances"
    );

    private static final Map<String, Integer> BY_PATH = buildIndex();

    private PlatformCatalogSortOrder() {
    }

    private static Map<String, Integer> buildIndex() {
        java.util.LinkedHashMap<String, Integer> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < ROOT_CHILDREN.size(); i++) {
            map.put(ROOT_CHILDREN.get(i), i);
        }
        for (int i = 0; i < PLATFORM_CHILDREN.size(); i++) {
            map.put(PLATFORM_CHILDREN.get(i), i);
        }
        return Map.copyOf(map);
    }

    public static OptionalInt forPath(String path) {
        Integer order = BY_PATH.get(path);
        return order != null ? OptionalInt.of(order) : OptionalInt.empty();
    }

    public static List<String> platformChildren() {
        return PLATFORM_CHILDREN;
    }

    public static List<String> rootChildren() {
        return ROOT_CHILDREN;
    }
}
