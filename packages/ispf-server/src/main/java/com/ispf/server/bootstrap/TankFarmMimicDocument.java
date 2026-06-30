package com.ispf.server.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * SCADA mimic document JSON for the anonymized tank-farm demo (exported from
 * {@code apps/web-console/src/scada/templates/tankFarmMimic.ts}).
 */
public final class TankFarmMimicDocument {

    public static final String DIAGRAM_JSON = loadDiagramJson();

    private static String loadDiagramJson() {
        try (InputStream in = TankFarmMimicDocument.class.getResourceAsStream("/bootstrap/tank-farm-mimic.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource bootstrap/tank-farm-mimic.json");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load tank-farm-mimic.json", e);
        }
    }

    private TankFarmMimicDocument() {
    }
}
