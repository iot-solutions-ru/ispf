package com.ispf.server.application.data;

import java.util.List;

public final class ApplicationSeedProfiles {

    private ApplicationSeedProfiles() {
    }

    public static List<ApplicationDataService.SeedScript> scripts(String profile) {
        return switch (profile) {
            case "smoke-demo" -> smokeDemo();
            default -> throw new IllegalArgumentException("Unknown seed profile: " + profile);
        };
    }

    private static List<ApplicationDataService.SeedScript> smokeDemo() {
        return List.of(
                new ApplicationDataService.SeedScript(
                        "categories",
                        """
                                INSERT INTO demo_category (id, category_code, status)
                                SELECT '11111111-1111-1111-1111-111111111111', 'CAT-A', 'open'
                                WHERE NOT EXISTS (
                                  SELECT 1 FROM demo_category WHERE category_code = 'CAT-A'
                                );
                                """
                ),
                new ApplicationDataService.SeedScript(
                        "items",
                        """
                                INSERT INTO demo_item (id, item_code, status, category_id)
                                SELECT '22222222-2222-2222-2222-222222222201', 'ITEM-001', 'ready',
                                       '11111111-1111-1111-1111-111111111111'
                                WHERE NOT EXISTS (SELECT 1 FROM demo_item WHERE item_code = 'ITEM-001');
                                INSERT INTO demo_item (id, item_code, status, category_id)
                                SELECT '22222222-2222-2222-2222-222222222202', 'ITEM-002', 'assigned',
                                       '11111111-1111-1111-1111-111111111111'
                                WHERE NOT EXISTS (SELECT 1 FROM demo_item WHERE item_code = 'ITEM-002');
                                """
                ),
                new ApplicationDataService.SeedScript(
                        "metrics",
                        """
                                INSERT INTO demo_metric (metric_key, metric_value, status)
                                SELECT 'throughput', 42, 'ok'
                                WHERE NOT EXISTS (SELECT 1 FROM demo_metric WHERE metric_key = 'throughput');
                                """
                )
        );
    }
}
