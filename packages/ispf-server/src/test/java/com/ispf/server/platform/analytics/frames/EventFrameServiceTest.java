package com.ispf.server.platform.analytics.frames;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventFrameServiceTest {

    @Mock
    private EventFrameStore store;

    @Mock
    private EventFrameRegistry registry;

    @Mock
    private EventFrameMesShiftBridge mesShiftBridge;

    private EventFrameService service;

    @BeforeEach
    void setUp() {
        service = new EventFrameService(store, registry, mesShiftBridge);
    }

    @Test
    void batchPhaseChangeClosesPreviousAndOpensNew() {
        Instant now = Instant.parse("2026-07-09T10:00:00Z");
        UUID previousId = UUID.randomUUID();
        EventFrame previous = new EventFrame(
                previousId,
                EventFrameType.BATCH,
                "root.platform.mes.lots.batch-1",
                "root.platform.mes.lots.batch-1",
                "root.platform.mes.lots.batch-1",
                "batch:charge",
                now.minus(1, ChronoUnit.HOURS),
                null,
                0,
                Map.of("phase", "charge")
        );
        when(registry.active("root.platform.mes.lots.batch-1", EventFrameType.BATCH))
                .thenReturn(Optional.of(previous));
        when(registry.get(previousId)).thenReturn(Optional.of(previous));

        service.onBatchPhaseChanged("root.platform.mes.lots.batch-1", "react", now);

        verify(store).close(eq(previousId), eq(now), eq(0));
        ArgumentCaptor<EventFrame> captor = ArgumentCaptor.forClass(EventFrame.class);
        verify(store).insert(captor.capture());
        EventFrame opened = captor.getValue();
        assertThat(opened.frameType()).isEqualTo(EventFrameType.BATCH);
        assertThat(opened.label()).isEqualTo("batch:react");
        assertThat(opened.metadata()).containsEntry("phase", "react");
    }

    @Test
    void openMesShiftUsesPlannedMinutesWindow() {
        Instant now = Instant.parse("2026-07-09T16:00:00Z");
        var shift = new EventFrameMesShiftBridge.MesShiftSnapshot(
                "dddddddd-dddd-dddd-dddd-dddddddddddd",
                "LINE-A01",
                "Morning",
                480,
                45
        );
        when(mesShiftBridge.loadShift(eq(EventFrameMesShiftBridge.DEFAULT_MES_APPLICATION_ID), eq(shift.shiftId())))
                .thenReturn(Optional.of(shift));
        when(mesShiftBridge.planShiftFrame(eq(shift), eq("root.platform.devices.hub"), any()))
                .thenReturn(new EventFrameMesShiftBridge.OpenShiftFramePlan(
                        "root.platform.devices.hub",
                        shift.shiftId(),
                        "Morning (LINE-A01)",
                        now.minus(8, ChronoUnit.HOURS),
                        null,
                        45,
                        Map.of("lineCode", "LINE-A01")
                ));

        EventFrame frame = service.openMesShift(shift.shiftId(), "root.platform.devices.hub");

        assertThat(frame.frameType()).isEqualTo(EventFrameType.SHIFT);
        assertThat(frame.downtimeMinutes()).isEqualTo(45);
        assertThat(frame.label()).isEqualTo("Morning (LINE-A01)");
        verify(registry).registerActive(any(EventFrame.class));
    }

    @Test
    void resolveQueryWindowClampsToFrameBounds() {
        UUID frameId = UUID.randomUUID();
        Instant started = Instant.parse("2026-07-09T08:00:00Z");
        Instant ended = Instant.parse("2026-07-09T16:00:00Z");
        EventFrame frame = new EventFrame(
                frameId,
                EventFrameType.SHIFT,
                "root.scope",
                null,
                "shift-1",
                "Morning",
                started,
                ended,
                30,
                Map.of()
        );
        when(registry.get(frameId)).thenReturn(Optional.of(frame));

        EventFrameService.EventFrameWindow window = service.resolveQueryWindow(
                frameId,
                Instant.parse("2026-07-09T09:00:00Z"),
                Instant.parse("2026-07-09T15:00:00Z")
        );

        assertThat(window.from()).isEqualTo(Instant.parse("2026-07-09T09:00:00Z"));
        assertThat(window.to()).isEqualTo(Instant.parse("2026-07-09T15:00:00Z"));
    }

    @Test
    void downtimeReportIncludesShiftDowntime() {
        UUID frameId = UUID.randomUUID();
        EventFrame frame = new EventFrame(
                frameId,
                EventFrameType.SHIFT,
                "root.scope",
                null,
                "shift-1",
                "Morning",
                Instant.parse("2026-07-09T08:00:00Z"),
                Instant.parse("2026-07-09T16:00:00Z"),
                10,
                Map.of()
        );
        when(store.listForScope(eq("root.scope"), any(), any())).thenReturn(List.of(frame));
        when(mesShiftBridge.loadShift(eq(EventFrameMesShiftBridge.DEFAULT_MES_APPLICATION_ID), eq("shift-1")))
                .thenReturn(Optional.of(
                new EventFrameMesShiftBridge.MesShiftSnapshot("shift-1", "LINE-A01", "Morning", 480, 45)
        ));

        List<EventFrameService.DowntimeFrameReportRow> rows = service.downtimeReport("root.scope");

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().downtimeMinutes()).isEqualTo(45);
    }
}
