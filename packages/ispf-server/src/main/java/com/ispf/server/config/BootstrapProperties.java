package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls optional platform bootstrap fixtures (demo objects, mini-TEC, lab devices).
 * Catalog skeleton and built-in models are always seeded on first start.
 */
@ConfigurationProperties(prefix = "ispf.bootstrap")
public class BootstrapProperties {

    /**
     * When false, skip demo/mini-TEC/lab preconfigurations; only system catalogs and built-in models.
     */
    private boolean fixturesEnabled = false;

    public boolean isFixturesEnabled() {
        return fixturesEnabled;
    }

    public void setFixturesEnabled(boolean fixturesEnabled) {
        this.fixturesEnabled = fixturesEnabled;
    }
}
