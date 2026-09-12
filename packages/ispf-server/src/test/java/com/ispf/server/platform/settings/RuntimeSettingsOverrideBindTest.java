package com.ispf.server.platform.settings;

import com.ispf.server.config.AiProperties;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.config.MqttGatewayProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeSettingsOverrideBindTest {

    private static final String DATA_DIR_KEY = "ispf.license.data-dir";

    @Test
    void springFactoriesRegistersOverrideProcessorForBoot4() throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<java.net.URL> urls = classLoader.getResources(SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION);
        boolean found = false;
        while (urls.hasMoreElements()) {
            Properties properties = new Properties();
            try (var in = urls.nextElement().openStream()) {
                properties.load(in);
            }
            String value = properties.getProperty("org.springframework.boot.EnvironmentPostProcessor");
            if (value != null && value.contains(RuntimeSettingsOverrideEnvironmentPostProcessor.class.getName())) {
                found = true;
                break;
            }
        }
        assertThat(found)
                .as("Boot 4 discovers EnvironmentPostProcessor from META-INF/spring.factories")
                .isTrue();
    }

    @Test
    void platformFileOverridesEveryCatalogDefaultWhenEnvVarAbsent(@TempDir Path temp) throws Exception {
        Path dataDir = Files.createDirectories(temp.resolve("data"));
        Map<String, String> fileValues = new LinkedHashMap<>();
        Map<String, Object> yamlDefaults = new LinkedHashMap<>();
        yamlDefaults.put("spring.config.location", "optional:classpath:missing-application.yml");
        yamlDefaults.put(DATA_DIR_KEY, dataDir.toAbsolutePath().toString());
        int index = 0;
        for (PlatformRuntimeSettingDefinition definition : PlatformRuntimeSettingsCatalog.all()) {
            if (DATA_DIR_KEY.equals(definition.propertyKey())) {
                continue;
            }
            fileValues.put(definition.propertyKey(), fileValue(definition, index));
            yamlDefaults.put(definition.propertyKey(), yamlDefault(definition));
            index++;
        }
        Files.writeString(dataDir.resolve("runtime-settings.properties"), toProperties(fileValues));

        SpringApplication application = new SpringApplication(EnvironmentApp.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(yamlDefaults);

        try (ConfigurableApplicationContext context = application.run()) {
            Environment environment = context.getEnvironment();
            fileValues.forEach((key, value) ->
                    assertThat(environment.getProperty(key))
                            .as("catalog key %s", key)
                            .isEqualTo(value));
        }
    }

    @Test
    void platformFileBindsAcrossSections(@TempDir Path temp) throws Exception {
        Path dataDir = Files.createDirectories(temp.resolve("data"));
        Files.writeString(dataDir.resolve("runtime-settings.properties"), """
                ispf.ai.provider=openai-compatible
                ispf.ai.model=from-platform
                ispf.ai.base-url=http://127.0.0.1:9/v1
                ispf.event-journal.store=clickhouse
                ispf.event-journal.batch-size=4242
                ispf.mqtt-gateway.ingress-dispatch-coalesce-enabled=false
                ispf.mqtt.enabled=true
                ispf.nats.enabled=true
                ispf.cluster.enabled=true
                """);

        SpringApplication application = new SpringApplication(BindApp.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.config.location", "optional:classpath:missing-application.yml",
                DATA_DIR_KEY, dataDir.toAbsolutePath().toString(),
                "ispf.ai.provider", "noop",
                "ispf.ai.model", "gpt-4o-mini",
                "ispf.ai.base-url", "",
                "ispf.event-journal.store", "jdbc",
                "ispf.event-journal.batch-size", "200",
                "ispf.mqtt-gateway.ingress-dispatch-coalesce-enabled", "true"
        ));

        try (ConfigurableApplicationContext context = application.run()) {
            AiProperties ai = context.getBean(AiProperties.class);
            assertThat(ai.getProvider()).isEqualTo("openai-compatible");
            assertThat(ai.getModel()).isEqualTo("from-platform");
            assertThat(ai.getBaseUrl()).isEqualTo("http://127.0.0.1:9/v1");

            EventJournalProperties journal = context.getBean(EventJournalProperties.class);
            assertThat(journal.getStore()).isEqualTo("clickhouse");
            assertThat(journal.getBatchSize()).isEqualTo(4242);

            MqttGatewayProperties mqtt = context.getBean(MqttGatewayProperties.class);
            assertThat(mqtt.isIngressDispatchCoalesceEnabled()).isFalse();

            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("ispf.mqtt.enabled")).isEqualTo("true");
            assertThat(environment.getProperty("ispf.nats.enabled")).isEqualTo("true");
            assertThat(environment.getProperty("ispf.cluster.enabled")).isEqualTo("true");
        }
    }

    private static String fileValue(PlatformRuntimeSettingDefinition definition, int index) {
        return switch (definition.type()) {
            case BOOLEAN -> (index % 2 == 0) ? "true" : "false";
            case INTEGER -> String.valueOf(42000 + index);
            case DURATION -> (index % 2 == 0) ? "9s" : "7s";
            case STRING -> "from-platform-" + index;
        };
    }

    private static String yamlDefault(PlatformRuntimeSettingDefinition definition) {
        return switch (definition.type()) {
            case BOOLEAN -> "true".equals(definition.defaultValue()) ? "false" : "true";
            case INTEGER -> "1";
            case DURATION -> "1s";
            case STRING -> "yaml-default";
        };
    }

    private static String toProperties(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        values.forEach((key, value) -> builder.append(key).append('=').append(value).append('\n'));
        return builder.toString();
    }

    @SpringBootConfiguration
    static class EnvironmentApp {
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties({AiProperties.class, EventJournalProperties.class, MqttGatewayProperties.class})
    static class BindApp {
    }
}
