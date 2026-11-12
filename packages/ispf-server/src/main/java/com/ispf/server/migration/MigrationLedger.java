package com.ispf.server.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Applied bundle scripts are identified by {@code script_id}, not by the manifest version.
 * Editing a script that already ran is rejected; a plant fork adds a new id.
 */
public final class MigrationLedger {

    private MigrationLedger() {
    }

    public static String checksum(String sql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sql.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Checksum failed", ex);
        }
    }

    /**
     * @param storedChecksum checksum already recorded for this script id, or null when the script is new
     */
    public static void requireUnchanged(String scriptId, String storedChecksum, String sql) {
        if (storedChecksum == null) {
            return;
        }
        String current = checksum(sql);
        if (!storedChecksum.equals(current)) {
            throw new IllegalStateException(
                    "Applied migration " + scriptId + " was edited; add a new script id instead of changing it"
            );
        }
    }
}
