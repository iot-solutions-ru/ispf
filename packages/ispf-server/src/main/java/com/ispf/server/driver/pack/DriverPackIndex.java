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
                Map<String, Map<String, Object>> raw = objectMapper.readValue(input, Map.class);
                for (Map<String, Object> entry : raw.values()) {
                    for (DriverPackIndexEntry indexEntry : DriverPackIndexEntry.fromCatalogEntry(entry)) {
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
        @SuppressWarnings("unchecked")
        static List<DriverPackIndexEntry> fromCatalogEntry(Map<String, Object> raw) {
            if (raw == null) {
                return List.of();
            }
            String packId = value(raw.get("packId"));
            String licenseType = value(raw.get("licenseType"));
            String jarFile = value(raw.get("jarFile"));
            if (packId.isBlank()) {
                return List.of();
            }
            Object driversNode = raw.get("drivers");
            if (driversNode instanceof List<?> drivers && !drivers.isEmpty()) {
                LinkedHashMap<String, DriverPackIndexEntry> indexed = new LinkedHashMap<>();
                for (Object item : drivers) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    String driverId = value(map.get("driverId"));
                    String driverClass = value(map.get("driverClass"));
                    if (driverId.isBlank()) {
                        continue;
                    }
                    indexed.putIfAbsent(driverId, new DriverPackIndexEntry(
                            packId,
                            driverId,
                            driverClass,
                            licenseType,
                            jarFile
                    ));
                }
                if (!indexed.isEmpty()) {
                    return List.copyOf(indexed.values());
                }
            }
            String driverId = value(raw.get("driverId"));
            if (driverId.isBlank()) {
                return List.of();
            }
            return List.of(new DriverPackIndexEntry(
                    packId,
                    driverId,
                    value(raw.get("driverClass")),
                    licenseType,
                    jarFile
            ));
        }

        private static String value(Object raw) {
            return raw == null ? "" : String.valueOf(raw).trim();
        }
    }
}
