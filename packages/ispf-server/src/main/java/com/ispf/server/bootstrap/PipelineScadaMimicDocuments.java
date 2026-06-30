package com.ispf.server.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Classpath JSON for pipeline SCADA mimic documents (exported from
 * {@code apps/web-console/src/scada/templates/pipeline-scada/}).
 */
public final class PipelineScadaMimicDocuments {

    public static final String MT_TERRITORIAL = load("pipeline-mt-territorial-mimic.json");
    public static final String MT_SCHEME = load("pipeline-mt-scheme-mimic.json");
    public static final String RP_OIL_PLACEMENT = load("pipeline-rp-oil-placement-mimic.json");
    public static final String RP = load("pipeline-rp-mimic.json");
    public static final String RP_URDO = load("pipeline-rp-urdo-mimic.json");
    public static final String SIKN = load("pipeline-sikn-mimic.json");
    public static final String PSP = load("pipeline-psp-mimic.json");
    public static final String NPS = load("pipeline-nps-mimic.json");
    public static final String LU_MT = load("pipeline-lu-mt-mimic.json");
    public static final String LU_NAV = load("pipeline-lu-nav-mimic.json");
    public static final String SEA_TERMINAL = load("pipeline-sea-terminal-mimic.json");
    public static final String PIER = load("pipeline-pier-mimic.json");
    public static final String MT_STOP_PANEL = load("pipeline-mt-stop-panel-mimic.json");
    public static final String MT_SECTION_PANEL = load("pipeline-mt-section-panel-mimic.json");
    public static final String NPS_PANEL = load("pipeline-nps-panel-mimic.json");

    private static String load(String resourceName) {
        String path = "/bootstrap/" + resourceName;
        try (InputStream in = PipelineScadaMimicDocuments.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resourceName, e);
        }
    }

    private PipelineScadaMimicDocuments() {
    }
}
