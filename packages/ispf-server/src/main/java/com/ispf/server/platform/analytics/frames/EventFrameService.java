package com.ispf.server.platform.analytics.frames;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventFrameService {

    private final EventFrameStore store;
    private final EventFrameRegistry registry;
    private final EventFrameMesShiftBridge mesShiftBridge;

    public EventFrameService(
            EventFrameStore store,
            EventFrameRegistry registry,
            EventFrameMesShiftBridge mesShiftBridge
    ) {
        this.store = store;
        this.registry = registry;
        this.mesShiftBridge = mesShiftBridge;
    }

    @Transactional
    public EventFrame open(OpenEventFrameCommand command) {
        validateOpen(command);
        closeActive(command.scopePath(), command.frameType(), command.startedAt());
        EventFrame frame = new EventFrame(
                UUID.randomUUID(),
                command.frameType(),
                command.scopePath(),
                command.sourcePath(),
                command.sourceKey(),
                command.label(),
                command.startedAt(),
                command.endedAt(),
                Math.max(command.downtimeMinutes(), 0),
                command.metadata() != null ? Map.copyOf(command.metadata()) : Map.of()
        );
        store.insert(frame);
        if (frame.active()) {
            registry.registerActive(frame);
        }
        return frame;
    }

    @Transactional
    public EventFrame openMesShift(String shiftId, String scopePath) {
        return openMesShift(EventFrameMesShiftBridge.DEFAULT_MES_APPLICATION_ID, shiftId, scopePath);
    }

    @Transactional
    public EventFrame openMesShift(String applicationId, String shiftId, String scopePath) {
        Instant now = Instant.now();
        EventFrameMesShiftBridge.MesShiftSnapshot shift = mesShiftBridge.loadShift(applicationId, shiftId)
                .orElseThrow(() -> new IllegalArgumentException("MES shift not found: " + shiftId));
        EventFrameMesShiftBridge.OpenShiftFramePlan plan = mesShiftBridge.planShiftFrame(shift, scopePath, now);
        return open(new OpenEventFrameCommand(
                EventFrameType.SHIFT,
                plan.scopePath(),
                null,
                plan.shiftId(),
                plan.label(),
                plan.startedAt(),
                plan.endedAt(),
                shift.downtimeMinutes(),
                plan.metadata()
        ));
    }

    @Transactional
    public void onBatchPhaseChanged(String lotPath, String phase, Instant observedAt) {
        Instant at = observedAt != null ? observedAt : Instant.now();
        if (phase == null || phase.isBlank() || "idle".equalsIgnoreCase(phase.trim())) {
            closeActive(lotPath, EventFrameType.BATCH, at);
            return;
        }
        open(new OpenEventFrameCommand(
                EventFrameType.BATCH,
                lotPath,
                lotPath,
                lotPath,
                "batch:" + phase,
                at,
                null,
                0,
                Map.of("phase", phase.trim())
        ));
    }

    @Transactional
    public EventFrame close(UUID frameId, Instant endedAt) {
        EventFrame existing = require(frameId);
        Instant end = endedAt != null ? endedAt : Instant.now();
        if (existing.endedAt() != null) {
            return existing;
        }
        int downtimeMinutes = existing.downtimeMinutes();
        if (existing.frameType() == EventFrameType.SHIFT && existing.sourceKey() != null) {
            downtimeMinutes = mesShiftBridge.loadShift(
                            EventFrameMesShiftBridge.DEFAULT_MES_APPLICATION_ID,
                            existing.sourceKey()
                    )
                    .map(EventFrameMesShiftBridge.MesShiftSnapshot::downtimeMinutes)
                    .orElse(downtimeMinutes);
        }
        store.close(frameId, end, downtimeMinutes);
        registry.unregister(existing);
        return new EventFrame(
                existing.frameId(),
                existing.frameType(),
                existing.scopePath(),
                existing.sourcePath(),
                existing.sourceKey(),
                existing.label(),
                existing.startedAt(),
                end,
                downtimeMinutes,
                existing.metadata()
        );
    }

    @Transactional(readOnly = true)
    public EventFrame require(UUID frameId) {
        return registry.get(frameId)
                .orElseThrow(() -> new IllegalArgumentException("Event frame not found: " + frameId));
    }

    @Transactional(readOnly = true)
    public Optional<EventFrame> find(UUID frameId) {
        return registry.get(frameId);
    }

    @Transactional(readOnly = true)
    public List<EventFrame> listActive(String scopePath) {
        return registry.listActive(scopePath);
    }

    @Transactional(readOnly = true)
    public Optional<EventFrame> activeShift(String scopePath) {
        return registry.active(scopePath, EventFrameType.SHIFT);
    }

    @Transactional(readOnly = true)
    public EventFrameWindow resolveQueryWindow(UUID frameId, Instant from, Instant to) {
        EventFrame frame = require(frameId);
        Instant now = Instant.now();
        Instant effectiveFrom = frame.startedAt();
        Instant effectiveTo = frame.effectiveEnd(now);
        if (from != null && from.isAfter(effectiveFrom)) {
            effectiveFrom = from;
        }
        if (to != null && to.isBefore(effectiveTo)) {
            effectiveTo = to;
        }
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("Event frame window is empty for frame " + frameId);
        }
        return new EventFrameWindow(frame.frameId(), frame.frameType(), effectiveFrom, effectiveTo);
    }

    @Transactional(readOnly = true)
    public Optional<EventFrameWindow> resolveShiftWindow(String scopePath, Instant now) {
        return activeShift(scopePath).map(frame -> new EventFrameWindow(
                frame.frameId(),
                frame.frameType(),
                frame.startedAt(),
                frame.effectiveEnd(now != null ? now : Instant.now())
        ));
    }

    @Transactional(readOnly = true)
    public List<DowntimeFrameReportRow> downtimeReport(String scopePath) {
        Instant now = Instant.now();
        Instant from = now.minusSeconds(365L * 24 * 3600);
        List<EventFrame> frames = store.listForScope(scopePath, from, now);
        List<DowntimeFrameReportRow> rows = new ArrayList<>();
        for (EventFrame frame : frames) {
            int downtime = frame.downtimeMinutes();
            if (frame.frameType() == EventFrameType.SHIFT && frame.sourceKey() != null) {
                downtime = mesShiftBridge.loadShift(
                                EventFrameMesShiftBridge.DEFAULT_MES_APPLICATION_ID,
                                frame.sourceKey()
                        )
                        .map(EventFrameMesShiftBridge.MesShiftSnapshot::downtimeMinutes)
                        .orElse(downtime);
            }
            rows.add(new DowntimeFrameReportRow(
                    frame.frameId(),
                    frame.frameType(),
                    frame.label(),
                    frame.startedAt(),
                    frame.endedAt(),
                    downtime
            ));
        }
        return rows;
    }

    private void closeActive(String scopePath, EventFrameType type, Instant endedAt) {
        registry.active(scopePath, type).ifPresent(frame -> close(frame.frameId(), endedAt));
    }

    private static void validateOpen(OpenEventFrameCommand command) {
        if (command.scopePath() == null || command.scopePath().isBlank()) {
            throw new IllegalArgumentException("scopePath is required");
        }
        if (command.label() == null || command.label().isBlank()) {
            throw new IllegalArgumentException("label is required");
        }
        if (command.startedAt() == null) {
            throw new IllegalArgumentException("startedAt is required");
        }
        if (command.endedAt() != null && !command.startedAt().isBefore(command.endedAt())) {
            throw new IllegalArgumentException("startedAt must be before endedAt");
        }
    }

    public record OpenEventFrameCommand(
            EventFrameType frameType,
            String scopePath,
            String sourcePath,
            String sourceKey,
            String label,
            Instant startedAt,
            Instant endedAt,
            int downtimeMinutes,
            Map<String, String> metadata
    ) {
    }

    public record EventFrameWindow(
            UUID frameId,
            EventFrameType frameType,
            Instant from,
            Instant to
    ) {
    }

    public record DowntimeFrameReportRow(
            UUID frameId,
            EventFrameType frameType,
            String label,
            Instant startedAt,
            Instant endedAt,
            int downtimeMinutes
    ) {
    }
}
