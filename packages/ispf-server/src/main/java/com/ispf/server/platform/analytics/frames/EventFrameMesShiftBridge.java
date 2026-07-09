package com.ispf.server.platform.analytics.frames;

import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Opens shift frames from MES {@code mes_oee_shift} rows when present (BL-208).
 */
@Component
public class EventFrameMesShiftBridge {

    public static final String DEFAULT_MES_APPLICATION_ID = "mes-platform";

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationDataStore applicationDataStore;
    private final ApplicationSchemaSession schemaSession;

    public EventFrameMesShiftBridge(
            JdbcTemplate jdbcTemplate,
            ApplicationDataStore applicationDataStore,
            ApplicationSchemaSession schemaSession
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationDataStore = applicationDataStore;
        this.schemaSession = schemaSession;
    }

    public Optional<MesShiftSnapshot> loadShift(String applicationId, String shiftId) {
        if (shiftId == null || shiftId.isBlank()) {
            return Optional.empty();
        }
        String appId = applicationId != null && !applicationId.isBlank()
                ? applicationId
                : DEFAULT_MES_APPLICATION_ID;
        return applicationDataStore.findApp(appId).flatMap(app -> {
            String schemaName = String.valueOf(app.get("schema_name"));
            MesShiftSnapshot[] holder = new MesShiftSnapshot[1];
            try {
                schemaSession.runInSchema(schemaName, () -> holder[0] = queryShift(shiftId));
            } catch (Exception ex) {
                return Optional.empty();
            }
            return Optional.ofNullable(holder[0]);
        });
    }

    private MesShiftSnapshot queryShift(String shiftId) {
        return jdbcTemplate.query(
                """
                        SELECT id::text, line_code, shift_label, planned_minutes, downtime_minutes
                        FROM mes_oee_shift
                        WHERE id::text = ?
                        """,
                (rs, rowNum) -> new MesShiftSnapshot(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getInt(5)
                ),
                shiftId
        ).stream().findFirst().orElse(null);
    }

    public OpenShiftFramePlan planShiftFrame(MesShiftSnapshot shift, String scopePath, Instant now) {
        int plannedMinutes = Math.max(shift.plannedMinutes(), 1);
        Duration window = Duration.ofMinutes(plannedMinutes);
        Instant startedAt = now.minus(window);
        String label = shift.shiftLabel() + " (" + shift.lineCode() + ")";
        Map<String, String> metadata = Map.of(
                "lineCode", shift.lineCode(),
                "shiftLabel", shift.shiftLabel(),
                "plannedMinutes", Integer.toString(plannedMinutes)
        );
        return new OpenShiftFramePlan(
                scopePath,
                shift.shiftId(),
                label,
                startedAt,
                null,
                shift.downtimeMinutes(),
                metadata
        );
    }

    public record MesShiftSnapshot(
            String shiftId,
            String lineCode,
            String shiftLabel,
            int plannedMinutes,
            int downtimeMinutes
    ) {
    }

    public record OpenShiftFramePlan(
            String scopePath,
            String shiftId,
            String label,
            Instant startedAt,
            Instant endedAt,
            int downtimeMinutes,
            Map<String, String> metadata
    ) {
    }
}
