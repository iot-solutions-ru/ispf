package com.ispf.server.driver.pack;

import java.util.List;
import java.util.Map;

public record DriverPackManifest(
        String packId,
        String minPlatformVersion,
        String jarFile,
        List<DriverEntry> drivers,
        Map<String, Object> license
) {

    public record DriverEntry(String driverId, String driverClass) {
    }

    @SuppressWarnings("unchecked")
    public static DriverPackManifest fromMap(Map<String, Object> root) {
        if (root == null) {
            return null;
        }
        List<DriverEntry> drivers = List.of();
        Object driversRaw = root.get("drivers");
        if (driversRaw instanceof List<?> list) {
            drivers = list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .map(entry -> new DriverEntry(
                            stringValue(entry.get("driverId")),
                            stringValue(entry.get("driverClass"))
                    ))
                    .filter(entry -> entry.driverId() != null && !entry.driverId().isBlank())
                    .toList();
        }
        Map<String, Object> license = null;
        Object licenseRaw = root.get("license");
        if (licenseRaw instanceof Map<?, ?> licenseMap) {
            license = (Map<String, Object>) licenseMap;
        }
        return new DriverPackManifest(
                stringValue(root.get("packId")),
                stringValue(root.get("minPlatformVersion")),
                stringValue(root.get("jarFile")),
                drivers,
                license
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
