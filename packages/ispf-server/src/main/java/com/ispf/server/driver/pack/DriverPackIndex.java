package com.ispf.server.driver.pack;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DriverPackIndex {

    private static final String RESOURCE = "/driver-pack/driver-packs.json";

    private final Map<String, DriverPackIndexEntry> byDriverId;
    private final Map<String, DriverPackIndexEntry> byPackId;

    public DriverPackIndex(ObjectMapper objectMapper) {
        Map<String, DriverPackIndexEntry> drivers = new LinkedHashMap<>();
        Map<String, DriverPackIndexEntry> packs = new LinkedHashMap<>();
        try (InputStream input = DriverPackIndex.class.getResourceAsStream(RESOURCE)) {
            if (input != null) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, String>> raw = objectMapper.readValue(input, Map.class);
                for (Map<String, String> entry : raw.values()) {
                    DriverPackIndexEntry indexEntry = DriverPackIndexEntry.fromMap(entry);
                    if (indexEntry != null) {
                        drivers.putIfAbsent(indexEntry.driverId(), indexEntry);
                        packs.putIfAbsent(indexEntry.packId(), indexEntry);
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load driver pack index", ex);
        }
        this.byDriverId = Collections.unmodifiableMap(drivers);
        this.byPackId = Collections.unmodifiableMap(packs);
    }

    public Optional<String> packIdFor(String driverId) {
        return Optional.ofNullable(byDriverId.get(driverId)).map(DriverPackIndexEntry::packId);
    }

    public List<DriverPackIndexEntry> entries() {
        return List.copyOf(byDriverId.values());
    }

    public record DriverPackIndexEntry(
            String packId,
            String driverId,
            String driverClass,
            String licenseType,
            String jarFile
    ) {
        static DriverPackIndexEntry fromMap(Map<String, String> raw) {
            if (raw == null) {
                return null;
            }
            String packId = value(raw.get("packId"));
            String driverId = value(raw.get("driverId"));
            if (packId.isBlank() || driverId.isBlank()) {
                return null;
            }
            return new DriverPackIndexEntry(
                    packId,
                    driverId,
                    value(raw.get("driverClass")),
                    value(raw.get("licenseType")),
                    value(raw.get("jarFile"))
            );
        }

        private static String value(String raw) {
            return raw == null ? "" : raw.trim();
        }
    }
}
