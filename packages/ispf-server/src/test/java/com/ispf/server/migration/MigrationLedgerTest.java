package com.ispf.server.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationLedgerTest {

    @Test
    void sameScriptIsStableAcrossManifestVersions() {
        String sql = "CREATE TABLE lot (id INT)";
        String checksum = MigrationLedger.checksum(sql);
        assertDoesNotThrow(() -> MigrationLedger.requireUnchanged("lot", checksum, sql));
    }

    @Test
    void editedAppliedScriptIsRejected() {
        String checksum = MigrationLedger.checksum("CREATE TABLE lot (id INT)");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> MigrationLedger.requireUnchanged("lot", checksum, "CREATE TABLE lot (id INT, plant_code TEXT)")
        );
        assertEquals(true, ex.getMessage().contains("lot"));
    }

    @Test
    void newScriptHasNoStoredChecksum() {
        assertDoesNotThrow(() -> MigrationLedger.requireUnchanged("plant_lot", null, "ALTER TABLE lot ADD plant_code TEXT"));
    }
}
