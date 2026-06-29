package com.ispf.server.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * SCADA mimic document JSON for mini-TEC single-line diagram (exported from
 * {@code apps/web-console/src/scada/templates/miniTecSld.ts}).
 */
public final class MiniTecMimicDocument {

    public static final String DIAGRAM_JSON = loadDiagramJson();

    private static String loadDiagramJson() {
        try (InputStream in = MiniTecMimicDocument.class.getResourceAsStream("/bootstrap/mini-tec-mimic.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource bootstrap/mini-tec-mimic.json");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load mini-tec-mimic.json", e);
        }
    }

    private MiniTecMimicDocument() {
    }
}
