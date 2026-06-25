package com.ispf.server.platform.settings;

import com.ispf.server.config.CommercialLicenseProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@Component
public class PlatformRuntimeSettingsStore {

    private final Path settingsFile;

    public PlatformRuntimeSettingsStore(CommercialLicenseProperties licenseProperties) {
        this.settingsFile = Path.of(licenseProperties.getDataDir(), "runtime-settings.properties").toAbsolutePath();
    }

    public Path settingsFile() {
        return settingsFile;
    }

    public Map<String, String> readOverrides() {
        if (!Files.isRegularFile(settingsFile)) {
            return Map.of();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(settingsFile)) {
            properties.load(reader);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read runtime settings: " + settingsFile, ex);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return map;
    }

    public void writeOverrides(Map<String, String> overrides) {
        try {
            Files.createDirectories(settingsFile.getParent());
            Properties properties = new Properties();
            properties.putAll(overrides);
            try (Writer writer = Files.newBufferedWriter(settingsFile)) {
                properties.store(writer, "ISPF runtime settings (UI / API overrides; restart unless hot-reloaded)");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write runtime settings: " + settingsFile, ex);
        }
    }
}
