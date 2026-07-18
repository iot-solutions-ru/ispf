package com.ispf.server.mimic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Default SCADA mimic diagram JSON (empty canvas + facility starter).
 */
public final class MimicLayouts {

    public static final String EMPTY_MIMIC = """
            {
              "version": 2,
              "width": 1600,
              "height": 900,
              "background": "var(--bg)",
              "grid": { "size": 1, "snap": false, "visible": false },
              "layers": [{ "id": "layer-default", "name": "Main", "visible": true }],
              "elements": [],
              "connections": []
            }
            """.trim();

    /** Starter P&ID for {@code root.platform.mimics.facility-overview} (scada-facility-overview template). */
    public static final String FACILITY_OVERVIEW_STARTER = loadFacilityOverviewStarter();

    private static String loadFacilityOverviewStarter() {
        try (InputStream in = MimicLayouts.class.getResourceAsStream("/bootstrap/facility-overview-mimic.json")) {
            if (in == null) {
                return EMPTY_MIMIC;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return EMPTY_MIMIC;
        }
    }

    private MimicLayouts() {
    }
}
