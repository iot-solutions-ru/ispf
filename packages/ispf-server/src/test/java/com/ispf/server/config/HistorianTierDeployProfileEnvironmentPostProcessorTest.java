package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class HistorianTierDeployProfileEnvironmentPostProcessorTest {

    private final HistorianTierDeployProfileEnvironmentPostProcessor processor =
            new HistorianTierDeployProfileEnvironmentPostProcessor();

    @Test
    void threeTierProfileEnablesWarmRoutingWhenNotExplicit() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(HistorianTierDeployProfileEnvironmentPostProcessor.DEPLOY_PROFILE_KEY, "three-tier");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY))
                .isEqualTo("true");
    }

    @Test
    void hotOnlyProfileDisablesWarmRoutingWhenNotExplicit() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(HistorianTierDeployProfileEnvironmentPostProcessor.DEPLOY_PROFILE_KEY, "hot-only");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY))
                .isEqualTo("false");
    }

    @Test
    void explicitWarmEnabledIsNotOverridden() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(HistorianTierDeployProfileEnvironmentPostProcessor.DEPLOY_PROFILE_KEY, "three-tier");
        environment.setProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY, "false");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY))
                .isEqualTo("false");
    }

    @Test
    void mssqlSingleEngineDisablesWarmAndSetsFlag() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(
                HistorianTierDeployProfileEnvironmentPostProcessor.DEPLOY_PROFILE_KEY, "mssql-single-engine");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY))
                .isEqualTo("false");
        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.SINGLE_ENGINE_KEY))
                .isEqualTo("true");
    }

    @Test
    void mysqlSingleEngineDisablesWarmAndSetsMysqlFlagOnly() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(
                HistorianTierDeployProfileEnvironmentPostProcessor.DEPLOY_PROFILE_KEY, "mysql-single-engine");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY))
                .isEqualTo("false");
        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.MYSQL_SINGLE_ENGINE_KEY))
                .isEqualTo("true");
        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.SINGLE_ENGINE_KEY))
                .isNull();
    }

    @Test
    void mysqlSingleEngineKeepsExplicitValues() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(
                HistorianTierDeployProfileEnvironmentPostProcessor.DEPLOY_PROFILE_KEY, "mysql-single-engine");
        environment.setProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY, "true");
        environment.setProperty(HistorianTierDeployProfileEnvironmentPostProcessor.MYSQL_SINGLE_ENGINE_KEY, "false");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY))
                .isEqualTo("true");
        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.MYSQL_SINGLE_ENGINE_KEY))
                .isEqualTo("false");
    }

    @Test
    void unknownProfileLeavesWarmUnset() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(HistorianTierDeployProfileEnvironmentPostProcessor.DEPLOY_PROFILE_KEY, "custom");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY))
                .isNull();
    }
}
