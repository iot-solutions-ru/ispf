package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls optional platform bootstrap fixtures (demo objects, lab devices)
 * and optional MES catalog seeding (MES ships as a marketplace product by default).
 * Industry twins (mini-TEC, tank-farm, OGP) are marketplace bundles — not fixture seed.
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

    /**
     * When true, seed {@code root.platform.mes.*} folders and MES INSTANCE blueprints ({@code batch-v1}, {@code work-order-v1})
     * on platform start. Default {@code false}: MES is a marketplace product (IoT Solutions), not base platform content.
     */
    private boolean mesCatalogEnabled = false;

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

    public boolean isMesCatalogEnabled() {
        return mesCatalogEnabled;
    }

    public void setMesCatalogEnabled(boolean mesCatalogEnabled) {
        this.mesCatalogEnabled = mesCatalogEnabled;
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
