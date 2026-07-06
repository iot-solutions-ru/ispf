package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls optional platform bootstrap fixtures (demo objects, mini-TEC, lab devices).
 * Catalog skeleton and built-in models are always seeded on first start.
 */
@ConfigurationProperties(prefix = "ispf.bootstrap")
public class BootstrapProperties {

    public static final String FIXTURE_PROFILE_FULL = "full";
    public static final String FIXTURE_PROFILE_MINI_TEC = "mini-tec";

    /**
     * When false, skip demo/mini-TEC/lab preconfigurations; only system catalogs and built-in models.
     */
    private boolean fixturesEnabled = false;

    /**
     * When fixtures are enabled: {@code full} seeds all reference demos; {@code mini-tec} seeds only mini-TEC.
     */
    private String fixtureProfile = FIXTURE_PROFILE_FULL;

    public boolean isFixturesEnabled() {
        return fixturesEnabled;
    }

    public void setFixturesEnabled(boolean fixturesEnabled) {
        this.fixturesEnabled = fixturesEnabled;
    }

    public String getFixtureProfile() {
        return fixtureProfile;
    }

    public void setFixtureProfile(String fixtureProfile) {
        this.fixtureProfile = fixtureProfile;
    }

    public boolean isMiniTecFixtureProfile() {
        return fixturesEnabled
                && FIXTURE_PROFILE_MINI_TEC.equalsIgnoreCase(fixtureProfile != null ? fixtureProfile.trim() : "");
    }

    /** Demo sensor, SNMP, lab, tank-farm, pipeline-scada and platform HMI operator app. */
    public boolean shouldSeedGeneralReferenceDemos() {
        return fixturesEnabled && !isMiniTecFixtureProfile();
    }
}
