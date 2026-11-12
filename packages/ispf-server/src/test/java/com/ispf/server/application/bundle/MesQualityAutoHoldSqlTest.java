package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesQualityAutoHoldSqlTest {

    private static final String HOLD = """
            UPDATE mes_batch_run
            SET phase = 'hold', hold_reason = ?, updated_at = CURRENT_TIMESTAMP
            WHERE batch_id = ? AND (CAST(? AS DOUBLE PRECISION) > CAST(? AS DOUBLE PRECISION) OR CAST(? AS DOUBLE PRECISION) < CAST(? AS DOUBLE PRECISION))
            """;

    @Test
    void sampleOutsideLimitsHoldsTheBatchAndAnInLimitSampleDoesNot() throws Exception {
        Path examples = examplesRoot();
        for (String bundle : new String[] {
                "examples/mes-platform/bundle.json",
                "examples/demo-plant/bundle.json",
                "examples/plant-discrete-mes/bundle.json"
        }) {
            String text = Files.readString(examples.getParent().resolve(bundle));
            assertTrue(text.contains("mes_quality_autoHold"), bundle);
            assertTrue(text.contains("mes_platform_spc_hold_v1"), bundle);
            assertTrue(text.contains("CAST(? AS DOUBLE PRECISION)"), bundle);
        }
        assertTrue(Files.exists(examples.resolve("demo-plant/sql/mes_platform_spc_hold_v1.sql")));
        assertTrue(Files.exists(examples.resolve("demo-plant/functions/mes_quality_autoHold.json")));
        assertTrue(Files.exists(examples.resolve("plant-discrete-mes/sql/mes_platform_spc_hold_v1.sql")));
        assertTrue(Files.exists(examples.resolve("plant-discrete-mes/functions/mes_quality_autoHold.json")));

        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        jdbc.execute("""
                CREATE TABLE mes_batch_run (
                    batch_path VARCHAR(256) PRIMARY KEY,
                    batch_id VARCHAR(64) NOT NULL,
                    recipe VARCHAR(64) NOT NULL,
                    phase VARCHAR(32) NOT NULL DEFAULT 'idle',
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    hold_reason VARCHAR(256)
                )
                """);
        jdbc.update(
                "INSERT INTO mes_batch_run (batch_path, batch_id, recipe, phase) VALUES ('p', 'B1', 'r', 'charge')"
        );

        assertEquals(0, jdbc.update(HOLD, "out", "B1", 10.0, 10.1, 10.0, 9.9));
        assertEquals("charge", phase(jdbc));

        assertEquals(1, jdbc.update(HOLD, "above UCL", "B1", 10.2, 10.1, 10.2, 9.9));
        assertEquals("hold", phase(jdbc));
        assertEquals("above UCL", jdbc.queryForObject(
                "SELECT hold_reason FROM mes_batch_run WHERE batch_id = 'B1'",
                String.class
        ));

        jdbc.update("UPDATE mes_batch_run SET phase = 'charge', hold_reason = NULL WHERE batch_id = 'B1'");
        assertEquals(1, jdbc.update(HOLD, "below LCL", "B1", 9.5, 10.1, 9.5, 9.9));
        assertEquals("hold", phase(jdbc));
        assertEquals("below LCL", jdbc.queryForObject(
                "SELECT hold_reason FROM mes_batch_run WHERE batch_id = 'B1'",
                String.class
        ));
    }

    private static String phase(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT phase FROM mes_batch_run WHERE batch_id = 'B1'", String.class);
    }

    private static Path examplesRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("examples"))) {
                return dir.resolve("examples");
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("examples/ not found from " + Path.of("").toAbsolutePath());
    }
}
