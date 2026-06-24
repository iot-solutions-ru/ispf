package com.ispf.driver.virtual;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;

/**
 * Synthetic telemetry for OGP print line ({@code profile=ogp-print-line}).
 */
public final class VirtualOgpPoll {

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();
    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();
    private static final DataSchema BOOL_VALUE = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private VirtualOgpPoll() {
    }

    public static void poll(DeviceDriver.DriverObject driverObject, OgpState state) {
        long now = System.currentTimeMillis();
        double dtSec = state.lastPollAt > 0 ? (now - state.lastPollAt) / 1000.0 : 0;
        state.lastPollAt = now;

        if (!state.machineStop && dtSec > 0) {
            state.meterM += state.speedMpm * dtSec / 60.0;
        }

        if (state.knifePulseTicks > 0) {
            state.knifePulseTicks--;
            if (state.knifePulseTicks == 0) {
                driverObject.updateVariable("knifeStrike", DataRecord.single(BOOL_VALUE, java.util.Map.of("value", false)));
            }
        }
        if (state.defectPulseTicks > 0) {
            state.defectPulseTicks--;
            if (state.defectPulseTicks == 0) {
                driverObject.updateVariable("defectButton", DataRecord.single(BOOL_VALUE, java.util.Map.of("value", false)));
            }
        }

        driverObject.updateVariable("windingRoll", DataRecord.single(STRING_VALUE, java.util.Map.of("value", state.windingRoll)));
        driverObject.updateVariable("speedMpm", DataRecord.single(DOUBLE_VALUE, java.util.Map.of("value", state.machineStop ? 0.0 : state.speedMpm)));
        driverObject.updateVariable("meterM", DataRecord.single(DOUBLE_VALUE, java.util.Map.of("value", state.meterM)));
        driverObject.updateVariable("machineStop", DataRecord.single(BOOL_VALUE, java.util.Map.of("value", state.machineStop)));
        driverObject.updateVariable("knifeStrike", DataRecord.single(BOOL_VALUE, java.util.Map.of("value", state.knifePulseTicks > 0)));
        driverObject.updateVariable("defectButton", DataRecord.single(BOOL_VALUE, java.util.Map.of("value", state.defectPulseTicks > 0)));
        driverObject.updateVariable("lastMachineEventLabel", DataRecord.single(STRING_VALUE, java.util.Map.of("value", state.lastEventLabel)));
    }

    public static void applySignal(OgpState state, String signal) {
        switch (signal == null ? "" : signal.toLowerCase()) {
            case "stop" -> {
                state.machineStop = true;
                state.lastEventLabel = "Останов";
            }
            case "resume" -> {
                state.machineStop = false;
                state.lastEventLabel = "";
            }
            case "knife" -> {
                state.knifePulseTicks = 3;
                state.lastEventLabel = "Удар ножа";
            }
            case "defect" -> {
                state.defectPulseTicks = 3;
                state.lastEventLabel = "Нажата кнопка \"Брак\" у машины";
            }
            default -> { }
        }
    }

    public static final class OgpState {
        public String windingRoll = "P1";
        public double speedMpm = 100;
        public double meterM;
        public boolean machineStop;
        public String lastEventLabel = "";
        public int knifePulseTicks;
        public int defectPulseTicks;
        public long lastPollAt;
    }
}
