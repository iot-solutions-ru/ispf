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
    void unknownProfileLeavesWarmUnset() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(HistorianTierDeployProfileEnvironmentPostProcessor.DEPLOY_PROFILE_KEY, "custom");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(HistorianTierDeployProfileEnvironmentPostProcessor.WARM_ENABLED_KEY))
                .isNull();
    }
}
