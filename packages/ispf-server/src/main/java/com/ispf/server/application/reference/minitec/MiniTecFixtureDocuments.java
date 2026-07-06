package com.ispf.server.application.reference.minitec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Classpath fixture documents for mini-TEC (dashboards, mimics).
 * Sync from prod: {@code deploy/export-minitec-fixtures.sh} on VPS, then import into
 * {@code src/main/resources/bootstrap/mini-tec/}.
 */
public final class MiniTecFixtureDocuments {

    private MiniTecFixtureDocuments() {
    }

    public static String dashboardLayout(String fileName) {
        return load("/bootstrap/mini-tec/dashboards/" + fileName + ".json");
    }

    private static String load(String resource) {
        try (InputStream in = MiniTecFixtureDocuments.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resource, e);
        }
    }
}
