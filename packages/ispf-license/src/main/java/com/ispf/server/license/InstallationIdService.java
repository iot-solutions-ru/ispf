package com.ispf.server.license;

import com.ispf.server.config.CommercialLicenseProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class InstallationIdService {

    private static final String FILE_NAME = ".ispf-installation-id";

    private final CommercialLicenseProperties properties;
    private volatile String cachedId;

    public InstallationIdService(CommercialLicenseProperties properties) {
        this.properties = properties;
    }

    public String ensureInstallationId() {
        if (cachedId != null && !cachedId.isBlank()) {
            return cachedId;
        }
        synchronized (this) {
            if (cachedId != null && !cachedId.isBlank()) {
                return cachedId;
            }
            Path file = installationIdPath();
            try {
                Files.createDirectories(file.getParent());
                if (Files.exists(file)) {
                    cachedId = Files.readString(file, StandardCharsets.UTF_8).trim().toLowerCase();
                    return cachedId;
                }
                String generated = fingerprint();
                Files.writeString(file, generated, StandardCharsets.UTF_8);
                cachedId = generated;
                return cachedId;
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read/write installation id: " + ex.getMessage(), ex);
            }
        }
    }

    public String currentInstallationId() {
        return cachedId != null ? cachedId : ensureInstallationId();
    }

    private Path installationIdPath() {
        return Path.of(properties.getDataDir()).resolve(FILE_NAME).normalize();
    }

    private static String fingerprint() {
        try {
            String seed = System.getenv().getOrDefault("COMPUTERNAME", "")
                    + "|" + System.getenv().getOrDefault("HOSTNAME", "")
                    + "|" + UUID.randomUUID();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
