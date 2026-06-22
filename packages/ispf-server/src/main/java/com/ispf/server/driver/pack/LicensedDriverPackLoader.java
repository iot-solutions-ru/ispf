package com.ispf.server.driver.pack;

import com.ispf.driver.DeviceDriver;
import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.config.DriverPackProperties;
import com.ispf.server.license.CommercialLicenseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

@Component
public class LicensedDriverPackLoader {

    private static final Logger log = LoggerFactory.getLogger(LicensedDriverPackLoader.class);
    private static final String MANIFEST_FILE = "driver-pack.json";

    private final DriverPackProperties packProperties;
    private final CommercialLicenseProperties licenseProperties;
    private final DriverPackLicenseVerifier licenseVerifier;
    private final LicensedDriverRegistry licensedDriverRegistry;
    private final ObjectMapper objectMapper;

    public LicensedDriverPackLoader(
            DriverPackProperties packProperties,
            CommercialLicenseProperties licenseProperties,
            DriverPackLicenseVerifier licenseVerifier,
            LicensedDriverRegistry licensedDriverRegistry,
            ObjectMapper objectMapper
    ) {
        this.packProperties = packProperties;
        this.licenseProperties = licenseProperties;
        this.licenseVerifier = licenseVerifier;
        this.licensedDriverRegistry = licensedDriverRegistry;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadPacksOnStartup() {
        Path packsRoot = Path.of(packProperties.getPacksDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(packsRoot)) {
            log.debug("Licensed driver packs directory not found: {}", packsRoot);
            return;
        }
        try (Stream<Path> entries = Files.list(packsRoot)) {
            entries.filter(Files::isDirectory).forEach(this::loadPackDirectory);
        } catch (IOException ex) {
            log.warn("Failed to scan driver packs directory {}: {}", packsRoot, ex.getMessage());
        }
    }

    void loadPackDirectory(Path packDir) {
        Path manifestPath = packDir.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestPath)) {
            return;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(manifestPath.toFile(), Map.class);
            DriverPackManifest manifest = DriverPackManifest.fromMap(root);
            if (manifest == null || manifest.packId().isBlank()) {
                log.warn("Skipping invalid driver pack manifest in {}", packDir);
                return;
            }
            if (manifest.jarFile() == null || manifest.jarFile().isBlank()) {
                log.warn("Driver pack {} missing jarFile", manifest.packId());
                return;
            }
            Path jarPath = packDir.resolve(manifest.jarFile()).normalize();
            if (!jarPath.startsWith(packDir.normalize()) || !Files.isRegularFile(jarPath)) {
                log.warn("Driver pack {} JAR not found: {}", manifest.packId(), jarPath);
                return;
            }

            DriverPackLicenseClaims claims = DriverPackLicenseClaims.fromMap(manifest.license());
            if (claims != null) {
                try {
                    licenseVerifier.verify(manifest.packId(), jarPath, claims);
                } catch (CommercialLicenseException ex) {
                    if (licenseProperties.isEnforce()) {
                        log.error("Skipping driver pack {}: {}", manifest.packId(), ex.getMessage());
                    } else {
                        log.warn("Skipping driver pack {} (license): {}", manifest.packId(), ex.getMessage());
                    }
                    return;
                }
            } else if (licenseProperties.isEnforce()) {
                log.warn("Skipping driver pack {} — license block required when enforce=true", manifest.packId());
                return;
            } else {
                log.warn("Loading driver pack {} without license (enforce=false)", manifest.packId());
            }

            registerPack(manifest, jarPath);
            log.info("Licensed driver pack loaded: {} from {}", manifest.packId(), packDir);
        } catch (Exception ex) {
            log.warn("Failed to load driver pack from {}: {}", packDir, ex.getMessage());
        }
    }

    private void registerPack(DriverPackManifest manifest, Path jarPath) throws IOException {
        URL jarUrl = jarPath.toUri().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[] {jarUrl}, DeviceDriver.class.getClassLoader());

        List<LicensedDriverRegistry.LicensedDriverBinding> bindings = new ArrayList<>();
        if (manifest.drivers() != null && !manifest.drivers().isEmpty()) {
            for (DriverPackManifest.DriverEntry entry : manifest.drivers()) {
                try {
                    bindings.add(bindingForClass(manifest.packId(), entry.driverId(), entry.driverClass(), classLoader));
                } catch (ClassNotFoundException ex) {
                    throw new IOException("Driver class not found for pack " + manifest.packId() + ": " + ex.getMessage(), ex);
                }
            }
        } else {
            ServiceLoader<DeviceDriver> serviceLoader = ServiceLoader.load(DeviceDriver.class, classLoader);
            for (DeviceDriver driver : serviceLoader) {
                bindings.add(bindingForInstance(manifest.packId(), driver));
            }
        }

        for (LicensedDriverRegistry.LicensedDriverBinding binding : bindings) {
            if (licensedDriverRegistry.contains(binding.driverId())) {
                log.warn("Driver id {} already registered — skipping duplicate from pack {}", binding.driverId(), manifest.packId());
                continue;
            }
            licensedDriverRegistry.register(binding);
        }
    }

    private static LicensedDriverRegistry.LicensedDriverBinding bindingForClass(
            String packId,
            String driverId,
            String driverClass,
            URLClassLoader classLoader
    ) throws ClassNotFoundException {
        if (driverClass == null || driverClass.isBlank()) {
            throw new IllegalArgumentException("driverClass is required for driverId " + driverId);
        }
        Class<?> type = Class.forName(driverClass, true, classLoader);
        if (!DeviceDriver.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(driverClass + " does not implement DeviceDriver");
        }
        @SuppressWarnings("unchecked")
        Class<? extends DeviceDriver> driverType = (Class<? extends DeviceDriver>) type;
        DeviceDriver prototype = instantiate(driverType);
        String resolvedId = driverId != null && !driverId.isBlank() ? driverId : prototype.metadata().id();
        return new LicensedDriverRegistry.LicensedDriverBinding(
                packId,
                resolvedId,
                prototype.metadata(),
                () -> instantiate(driverType)
        );
    }

    private static LicensedDriverRegistry.LicensedDriverBinding bindingForInstance(String packId, DeviceDriver driver) {
        return new LicensedDriverRegistry.LicensedDriverBinding(
                packId,
                driver.metadata().id(),
                driver.metadata(),
                () -> instantiate(driver.getClass())
        );
    }

    private static DeviceDriver instantiate(Class<? extends DeviceDriver> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to instantiate driver " + type.getName(), ex);
        }
    }
}
