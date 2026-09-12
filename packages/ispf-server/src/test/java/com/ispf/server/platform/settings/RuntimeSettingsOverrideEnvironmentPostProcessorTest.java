package com.ispf.server.platform.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeSettingsOverrideEnvironmentPostProcessorTest {

    @Test
    void overrideFileBeatsLaterPropertySources(@TempDir Path temp) throws Exception {
        Path dataDir = temp.resolve("data");
        Files.createDirectories(dataDir);
        Path settingsFile = dataDir.resolve("runtime-settings.properties");
        Files.writeString(settingsFile, """
                ispf.ai.enabled=false
                ispf.object-change.elastic-scale-up-queue-threshold=99
                """);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("ispf.license.data-dir", dataDir.toString());
        environment.setProperty("ispf.ai.enabled", "true");
        environment.setProperty("ispf.object-change.elastic-scale-up-queue-threshold", "50");

        new RuntimeSettingsOverrideEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("ispf.ai.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("ispf.object-change.elastic-scale-up-queue-threshold")).isEqualTo("99");
        assertThat(environment.getPropertySources().contains(RuntimeSettingsOverrideEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isTrue();
    }

    @Test
    void fileAiModelBeatsProfileYamlWhenEnvVarAbsent(@TempDir Path temp) throws Exception {
        Path dataDir = temp.resolve("data");
        Files.createDirectories(dataDir);
        Path settingsFile = dataDir.resolve("runtime-settings.properties");
        Files.writeString(settingsFile, """
                ispf.ai.model=deepseek-v4-flash
                ispf.ai.base-url=https://api.deepseek.com/v1
                """);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("ispf.license.data-dir", dataDir.toString());
        environment.setProperty("ispf.ai.model", "unsloth/Qwen3.6-35B-A3B-NVFP4");
        environment.setProperty("ispf.ai.base-url", "http://lab-edge.example.invalid:8000/v1");

        new RuntimeSettingsOverrideEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("ispf.ai.model")).isEqualTo("deepseek-v4-flash");
        assertThat(environment.getProperty("ispf.ai.base-url")).isEqualTo("https://api.deepseek.com/v1");
    }

    @Test
    void resolvesSettingsFileFromLicenseDataDir(@TempDir Path temp) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("ispf.license.data-dir", temp.toString());
        Path resolved = RuntimeSettingsOverrideEnvironmentPostProcessor.resolveSettingsFile(environment);
        assertThat(resolved).isEqualTo(temp.resolve("runtime-settings.properties").toAbsolutePath().normalize());
    }
}
