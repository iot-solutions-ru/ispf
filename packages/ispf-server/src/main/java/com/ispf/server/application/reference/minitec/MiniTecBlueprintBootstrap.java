package com.ispf.server.application.reference.minitec;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.server.application.reference.minitec.MiniTecFunctionScripts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * INSTANCE models for the generic mini-TEC digital twin.
 */
@Component
public class MiniTecBlueprintBootstrap {

    public static final String GPU_MODEL = "mini-tec-gpu-v1";
    public static final String GRPB_MODEL = "mini-tec-grpb-v1";
    public static final String RUMB_MODEL = "mini-tec-rumb-v1";
    public static final String DGU_MODEL = "mini-tec-dgu-v1";
    public static final String LOAD_MODEL = "mini-tec-load-module-v1";
    public static final String HUB_MODEL = "mini-tec-station-hub-v1";

    public static final String GPU_DRIVER_CONFIG_TEMPLATE =
            "{\"profile\":\"tec-gpu\",\"ratedPowerKw\":\"%s\",\"unitIndex\":\"%d\"}";
    public static final String GRPB_DRIVER_CONFIG = "{\"profile\":\"tec-grpb\"}";
    public static final String RUMB_DRIVER_CONFIG = "{\"profile\":\"tec-rumb\"}";
    public static final String DGU_DRIVER_CONFIG = "{\"profile\":\"tec-dgu\"}";
    public static final String LOAD_DRIVER_CONFIG = "{\"profile\":\"tec-load\"}";

    public static final String TEC_POINT_MAPPINGS = "{\"status\":\"sim\"}";

    private static final DataSchema MEAS = DataSchema.builder("measurement")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();
    private static final DataSchema BOOL = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema INT = DataSchema.builder("intValue")
            .field("value", FieldType.INTEGER)
            .build();
    private static final DataSchema STR = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final BlueprintEngine BlueprintEngine;
    private final BlueprintRegistry BlueprintRegistry;

    public MiniTecBlueprintBootstrap(BlueprintEngine BlueprintEngine, BlueprintRegistry BlueprintRegistry) {
        this.BlueprintEngine = BlueprintEngine;
        this.BlueprintRegistry = BlueprintRegistry;
    }

    public void ensureMiniTecModels() {
        ensure(buildGpuModel());
        ensure(buildGrpbModel());
        ensure(buildRumbModel());
        ensure(buildDguModel());
        ensure(buildLoadModel());
        ensure(buildHubModel());
    }

    private void ensure(BlueprintDefinition desired) {
        var existing = BlueprintRegistry.findByName(desired.name());
        if (existing.isEmpty()) {
            BlueprintEngine.createBlueprint(desired);
            return;
        }
        BlueprintDefinition current = existing.get();
        if (!needsModelRefresh(current, desired)) {
            return;
        }
        BlueprintEngine.updateBlueprint(new BlueprintDefinition(
                current.id(),
                desired.name(),
                desired.description(),
                desired.type(),
                desired.targetObjectType(),
                desired.suitabilityExpression(),
                desired.variables(),
                desired.events(),
                desired.functions(),
                desired.bindingRules(),
                desired.parameters(),
                current.createdAt(),
                Instant.now()
        ));
    }

    private static boolean needsModelRefresh(BlueprintDefinition current, BlueprintDefinition desired) {
        if (!hasAllEvents(current, desired)) {
            return true;
        }
        for (BlueprintVariableDefinition variable : desired.variables()) {
            boolean found = current.variables().stream().anyMatch(v -> v.name().equals(variable.name()));
            if (!found) {
                return true;
            }
        }
        for (FunctionDescriptor function : desired.functions()) {
            boolean found = current.functions().stream().anyMatch(f -> f.name().equals(function.name()));
            if (!found) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAllEvents(BlueprintDefinition current, BlueprintDefinition desired) {
        for (EventDescriptor event : desired.events()) {
            boolean found = current.events().stream().anyMatch(item -> item.name().equals(event.name()));
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static EventDescriptor boolEvent(String name, String description, EventLevel level) {
        return new EventDescriptor(name, description, BOOL, level);
    }

    private static BlueprintVariableDefinition meas(String name, String desc, String group, String unit, double def) {
        return BlueprintVariableDefinition.withHistory(
                name, desc, group, MEAS, true, false,
                DataRecord.single(MEAS, Map.of("value", def, "unit", unit))
        );
    }

    private static BlueprintVariableDefinition boolVar(String name, String desc, String group, boolean def, boolean writable) {
        return BlueprintVariableDefinition.of(
                name, desc, group, BOOL, true, writable,
                DataRecord.single(BOOL, Map.of("value", def))
        );
    }

    private static BlueprintVariableDefinition strVar(String name, String desc, String group, String def, boolean writable) {
        return BlueprintVariableDefinition.of(
                name, desc, group, STR, true, writable,
                DataRecord.single(STR, Map.of("value", def))
        );
    }

    private static BlueprintVariableDefinition equipmentStatusVar() {
        return strVar("equipmentStatus", "Equipment status (NORMAL/WARNING/ALARM/OFFLINE/MAINTENANCE)", "status", "NORMAL", false);
    }

    private static BlueprintVariableDefinition intVar(String name, String desc, String group, int def, boolean writable) {
        return BlueprintVariableDefinition.of(
                name, desc, group, INT, true, writable,
                DataRecord.single(INT, Map.of("value", def))
        );
    }

    private static List<BlueprintVariableDefinition> driverVars(String configJson) {
        return List.of(
                BlueprintVariableDefinition.of("driverId", "Driver id", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", "virtual"))),
                BlueprintVariableDefinition.of("driverConfigJson", "Driver config", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", configJson))),
                BlueprintVariableDefinition.of("driverPointMappingsJson", "Point mappings", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", TEC_POINT_MAPPINGS))),
                BlueprintVariableDefinition.of("driverPollIntervalMs", "Poll interval ms", "driver", INT, true, true,
                        DataRecord.single(INT, Map.of("value", 2000))),
                boolVar("driverAutoStart", "Auto-start driver", "driver", true, true)
        );
    }

    private BlueprintDefinition buildGpuModel() {
        List<BlueprintVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("jacketWaterTemp", "Jacket water temperature", "telemetry", "C", 75));
        vars.add(meas("jacketWaterPressure", "Jacket water pressure", "telemetry", "bar", 1.8));
        vars.add(meas("lubeOilTemp", "Lube oil temperature", "telemetry", "C", 70));
        vars.add(meas("lubeOilPressure", "Lube oil pressure", "telemetry", "bar", 4.5));
        vars.add(meas("mixtureTemp", "Mixture temperature", "telemetry", "C", 40));
        vars.add(meas("coolantTemp", "Coolant temperature", "telemetry", "C", 68));
        vars.add(meas("coolantPressure", "Coolant pressure", "telemetry", "bar", 1.2));
        vars.add(meas("exhaustGasTemp", "Exhaust gas temperature", "telemetry", "C", 420));
        vars.add(meas("boostPressure", "Boost pressure", "telemetry", "bar", 0.8));
        vars.add(meas("oilLevelMin", "Oil level min", "telemetry", "%", 45));
        vars.add(meas("oilLevelMax", "Oil level max", "telemetry", "%", 85));
        vars.add(meas("rpm", "Engine speed", "telemetry", "rpm", 0));
        vars.add(meas("activePowerKw", "Active power", "telemetry", "kW", 0));
        vars.add(meas("reactivePowerKvar", "Reactive power", "telemetry", "kVAr", 0));
        vars.add(meas("excitationVoltage", "Excitation voltage", "telemetry", "V", 0));
        vars.add(meas("windingTemp", "Winding temperature", "telemetry", "C", 85));
        vars.add(meas("throttlePosition", "Throttle position", "telemetry", "%", 0));
        vars.add(meas("gasMixerPosition", "Gas mixer position", "telemetry", "%", 40));
        vars.add(meas("gasConcentration", "Gas concentration", "telemetry", "%LEL", 0));
        vars.add(meas("runningHours", "Running hours", "exploitation", "h", 0));
        vars.add(meas("energyKwh", "Active energy", "exploitation", "kWh", 0));
        vars.add(meas("reactiveEnergyKvarh", "Reactive energy", "exploitation", "kVArh", 0));
        vars.add(boolVar("detonation", "Detonation", "status", false, false));
        vars.add(boolVar("running", "Running", "status", false, false));
        vars.add(boolVar("synced", "Synchronized", "status", false, false));
        vars.add(boolVar("manualMode", "Manual mode", "status", false, true));
        vars.add(boolVar("remoteEnabled", "Remote enabled", "status", true, false));
        vars.add(intVar("startCount", "Start count", "exploitation", 0, false));
        vars.add(boolVar("cmdStart", "Start command", "control", false, true));
        vars.add(boolVar("cmdStop", "Stop command", "control", false, true));
        vars.add(boolVar("cmdSync", "Sync command", "control", false, true));
        vars.add(meas("setpointPowerKw", "Power setpoint", "control", "kW", 1000));
        vars.add(boolVar("protOverload", "Overload protection", "protection", false, false));
        vars.add(boolVar("protOvervoltage", "Overvoltage protection", "protection", false, false));
        vars.add(boolVar("protUndervoltage", "Undervoltage protection", "protection", false, false));
        vars.add(boolVar("protAsymmetry", "Asymmetry protection", "protection", false, false));
        vars.add(boolVar("protFrequency", "Frequency protection", "protection", false, false));
        vars.add(boolVar("protExciter", "Exciter protection", "protection", false, false));
        vars.add(equipmentStatusVar());
        vars.addAll(driverVars(String.format(GPU_DRIVER_CONFIG_TEMPLATE, "1480", 1)));

        List<BlueprintBindingRule> bindings = List.of(
                BlueprintBindingRule.of("equip-status", "equipmentStatus",
                        "self.protOverload[\"value\"] == true || self.protFrequency[\"value\"] == true || self.detonation[\"value\"] == true ? \"ALARM\" : (self.manualMode[\"value\"] == true ? \"MAINTENANCE\" : (self.running[\"value\"] == true ? \"NORMAL\" : \"STOPPED\"))"),
                BlueprintBindingRule.of("prot-overload", "protOverload",
                        "self.activePowerKw[\"value\"] > 1550"),
                BlueprintBindingRule.of("prot-overvoltage", "protOvervoltage",
                        "self.excitationVoltage[\"value\"] > 160"),
                BlueprintBindingRule.of("prot-undervoltage", "protUndervoltage",
                        "self.running[\"value\"] == true && self.excitationVoltage[\"value\"] < 80"),
                BlueprintBindingRule.of("prot-frequency", "protFrequency",
                        "self.running[\"value\"] == true && (self.rpm[\"value\"] < 1400 || self.rpm[\"value\"] > 1600)"),
                BlueprintBindingRule.of("prot-exciter", "protExciter",
                        "self.excitationVoltage[\"value\"] > 180")
        );

        return model(
                GPU_MODEL,
                "Mini-TEC gas piston unit",
                ObjectType.DEVICE,
                vars,
                bindings,
                List.of(boolEvent("gpuProtOverload", "GPU overload protection", EventLevel.ERROR),
                        boolEvent("gpuProtOverloadCleared", "GPU overload cleared", EventLevel.INFO)),
                List.of(
                        MiniTecFunctionScripts.gpuStartFn(),
                        MiniTecFunctionScripts.gpuStopFn(),
                        MiniTecFunctionScripts.gpuSyncFn()
                )
        );
    }

    private BlueprintDefinition buildGrpbModel() {
        List<BlueprintVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("gasOutletPressure", "Gas outlet pressure", "telemetry", "bar", 2.5));
        vars.add(meas("gasFlowRate", "Gas flow rate", "telemetry", "m3/h", 0));
        vars.add(meas("gasVolume", "Gas volume", "telemetry", "m3", 0));
        vars.add(meas("dpMeter", "Meter differential pressure", "telemetry", "bar", 0));
        vars.add(meas("dpFilter", "Filter differential pressure", "telemetry", "bar", 0));
        vars.add(meas("valvePosition", "Valve position", "telemetry", "%", 80));
        vars.add(meas("ambientTemp", "Ambient temperature", "telemetry", "C", 18));
        vars.add(boolVar("pzkTripped", "PZK tripped", "protection", false, false));
        vars.add(boolVar("gasLeak", "Gas leak", "protection", false, false));
        vars.add(boolVar("unauthorizedAccess", "Unauthorized access", "status", false, false));
        vars.add(boolVar("fireAlarm", "Fire alarm", "protection", false, false));
        vars.add(boolVar("cmdValveOpen", "Open valve", "control", false, true));
        vars.add(boolVar("cmdValveClose", "Close valve", "control", false, true));
        vars.add(boolVar("cmdPzkReset", "Reset PZK", "control", false, true));
        vars.add(boolVar("cmdGasTrip", "Emergency gas trip", "control", false, true));
        vars.add(boolVar("cmdSimulateFire", "Simulate fire (training)", "control", false, true));
        vars.add(boolVar("cmdSimulateGasLeak", "Simulate gas leak (training)", "control", false, true));
        vars.add(equipmentStatusVar());
        vars.addAll(driverVars(GRPB_DRIVER_CONFIG));
        return model(
                GRPB_MODEL,
                "Gas regulating point block",
                ObjectType.DEVICE,
                vars,
                List.of(BlueprintBindingRule.of("equip-status", "equipmentStatus",
                        "self.fireAlarm[\"value\"] == true || self.gasLeak[\"value\"] == true ? \"ALARM\" : (self.pzkTripped[\"value\"] == true ? \"WARNING\" : \"NORMAL\")")),
                List.of(
                        boolEvent("grpbFire", "GRPB fire alarm", EventLevel.ERROR),
                        boolEvent("grpbGasLeak", "GRPB gas leak", EventLevel.ERROR),
                        boolEvent("grpbFireCleared", "GRPB fire cleared", EventLevel.INFO),
                        boolEvent("grpbGasLeakCleared", "GRPB gas leak cleared", EventLevel.INFO)
                ),
                List.of(
                        MiniTecFunctionScripts.grpbValveFn(),
                        MiniTecFunctionScripts.grpbPzkResetFn(),
                        MiniTecFunctionScripts.simulateFireFn(),
                        MiniTecFunctionScripts.simulateGasLeakFn()
                )
        );
    }

    private BlueprintDefinition buildRumbModel() {
        List<BlueprintVariableDefinition> vars = new ArrayList<>();
        vars.add(boolVar("breakerClosed", "Breaker closed", "status", true, false));
        vars.add(boolVar("breakerPosition", "Breaker position", "status", true, false));
        vars.add(boolVar("cartPosition", "Cart position", "status", true, false));
        vars.add(boolVar("emergencyStop", "Emergency stop", "status", false, false));
        vars.add(boolVar("terminalFault", "Terminal fault", "status", false, false));
        vars.add(boolVar("groundingSwitchClosed", "Grounding switch closed", "status", false, false));
        vars.add(boolVar("interlockOk", "Interlock OK", "status", true, false));
        vars.add(boolVar("circuitFault", "Circuit fault", "status", false, false));
        vars.add(boolVar("cmdBreakerClose", "Close breaker", "control", false, true));
        vars.add(boolVar("cmdBreakerOpen", "Open breaker", "control", false, true));
        vars.add(equipmentStatusVar());
        vars.addAll(driverVars(RUMB_DRIVER_CONFIG));
        return model(
                RUMB_MODEL,
                "Switchgear 10/0.4 kV",
                ObjectType.DEVICE,
                vars,
                List.of(BlueprintBindingRule.of("equip-status", "equipmentStatus",
                        "self.emergencyStop[\"value\"] == true || self.circuitFault[\"value\"] == true ? \"ALARM\" : \"NORMAL\"")),
                List.of(MiniTecFunctionScripts.breakerOperateFn())
        );
    }

    private BlueprintDefinition buildDguModel() {
        List<BlueprintVariableDefinition> vars = new ArrayList<>();
        vars.add(boolVar("cmdStart", "Start DGU", "control", false, true));
        vars.add(boolVar("cmdStop", "Stop DGU", "control", false, true));
        vars.add(boolVar("running", "DGU running", "status", false, false));
        vars.add(boolVar("batteryCharging", "Battery charging", "status", true, false));
        vars.add(meas("fuelLevelPct", "Fuel level", "telemetry", "%", 85));
        vars.add(meas("coolantTemp", "Coolant temperature", "telemetry", "C", 25));
        vars.add(meas("activePowerKw", "Active power", "telemetry", "kW", 0));
        vars.add(equipmentStatusVar());
        vars.addAll(driverVars(DGU_DRIVER_CONFIG));
        return model(
                DGU_MODEL,
                "Diesel generator unit",
                ObjectType.DEVICE,
                vars,
                List.of(BlueprintBindingRule.of("equip-status", "equipmentStatus",
                        "self.running[\"value\"] == true ? \"NORMAL\" : \"STOPPED\"")),
                List.of(MiniTecFunctionScripts.dguStartFn(), MiniTecFunctionScripts.dguStopFn())
        );
    }

    private BlueprintDefinition buildLoadModel() {
        List<BlueprintVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("activePowerKw", "Active power", "telemetry", "kW", 0));
        vars.add(meas("reactivePowerKvar", "Reactive power", "telemetry", "kVAr", 0));
        vars.add(meas("apparentPowerKva", "Apparent power", "telemetry", "kVA", 0));
        vars.add(meas("frequencyHz", "Frequency", "telemetry", "Hz", 50));
        vars.add(meas("currentA", "Phase A current", "telemetry", "A", 0));
        vars.add(meas("currentB", "Phase B current", "telemetry", "A", 0));
        vars.add(meas("currentC", "Phase C current", "telemetry", "A", 0));
        vars.add(meas("voltageAB", "Voltage AB", "telemetry", "V", 400));
        vars.add(meas("voltageBC", "Voltage BC", "telemetry", "V", 400));
        vars.add(meas("voltageCA", "Voltage CA", "telemetry", "V", 400));
        vars.add(meas("voltageA", "Voltage A", "telemetry", "V", 230));
        vars.add(meas("voltageB", "Voltage B", "telemetry", "V", 230));
        vars.add(meas("voltageC", "Voltage C", "telemetry", "V", 230));
        vars.add(meas("loadSetpointPct", "Load setpoint", "control", "%", 35));
        vars.add(boolVar("cmdSetLoad", "Apply load setpoint", "control", false, true));
        vars.add(equipmentStatusVar());
        vars.addAll(driverVars(LOAD_DRIVER_CONFIG));
        return model(
                LOAD_MODEL,
                "Resistive load module ME-1500-K3",
                ObjectType.DEVICE,
                vars,
                List.of(BlueprintBindingRule.of("equip-status", "equipmentStatus", "\"NORMAL\"")),
                List.of(MiniTecFunctionScripts.loadModuleSetLoadFn())
        );
    }

    private BlueprintDefinition buildHubModel() {
        List<BlueprintVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("totalGenPowerKw", "Total generation", "telemetry", "kW", 0));
        vars.add(meas("totalReactiveKvar", "Total reactive power", "telemetry", "kVAr", 0));
        vars.add(meas("totalLoadKw", "Total load", "telemetry", "kW", 4130));
        vars.add(meas("loadMarginKw", "Load margin", "telemetry", "kW", 310));
        vars.add(meas("bus10kvVoltage", "10 kV bus voltage", "telemetry", "kV", 10.5));
        vars.add(meas("bus04kvVoltage", "0.4 kV bus voltage", "telemetry", "kV", 0.4));
        vars.add(meas("gridFrequencyHz", "Grid frequency", "telemetry", "Hz", 50));
        vars.add(meas("consumerLoad1Kw", "Consumer 1 load", "telemetry", "kW", 2430));
        vars.add(meas("consumerLoad2Kw", "Consumer 2 load", "telemetry", "kW", 1200));
        vars.add(meas("consumerLoad3Kw", "Reserve load", "telemetry", "kW", 500));
        vars.add(boolVar("islandMode", "Island mode", "status", true, false));
        vars.add(BlueprintVariableDefinition.of("plantState", "Plant state", "status", STR, true, false,
                DataRecord.single(STR, Map.of("value", "RUNNING"))));
        vars.add(boolVar("millLoadPending", "Mill load pending manual", "status", false, true));
        vars.add(boolVar("alarmLatched", "Alarm latched", "protection", false, true));
        vars.add(boolVar("busOvervoltage", "Bus overvoltage", "protection", false, false));
        vars.add(boolVar("busUndervoltage", "Bus undervoltage", "protection", false, false));
        vars.add(boolVar("busFrequencyLow", "Bus frequency low", "protection", false, false));
        vars.add(boolVar("busFrequencyHigh", "Bus frequency high", "protection", false, false));
        vars.add(boolVar("stationUnderpower", "Station underpower", "protection", false, false));
        vars.add(boolVar("gpuSyncFault", "GPU sync fault", "protection", false, false));
        vars.add(boolVar("highlightActive", "Mimic highlight banner active", "status", false, false));
        vars.add(meas("plantAvailabilityPct", "Plant availability", "kpi", "%", 95));
        vars.add(meas("mtbfHours", "MTBF", "kpi", "h", 720));
        vars.add(meas("mttrHours", "MTTR", "kpi", "h", 2));
        vars.add(equipmentStatusVar());

        List<BlueprintBindingRule> bindings = List.of(
                refRule("gen-gpu1", "gpu1Power", MiniTecPaths.GPU_01, "activePowerKw"),
                refRule("gen-gpu2", "gpu2Power", MiniTecPaths.GPU_02, "activePowerKw"),
                refRule("gen-gpu3", "gpu3Power", MiniTecPaths.GPU_03, "activePowerKw"),
                sumRule("total-gen", "totalGenPowerKw", "self.gpu1Power[\"value\"] + self.gpu2Power[\"value\"] + self.gpu3Power[\"value\"]"),
                sumRule("total-reactive", "totalReactiveKvar", "self.totalGenPowerKw[\"value\"] * 0.15"),
                sumRule("total-load", "totalLoadKw",
                        "self.consumerLoad1Kw[\"value\"] + self.consumerLoad2Kw[\"value\"] + self.consumerLoad3Kw[\"value\"]"),
                sumRule("load-margin", "loadMarginKw", "4440 - self.totalLoadKw[\"value\"]"),
                sumRule("bus-freq", "gridFrequencyHz", readRef(MiniTecPaths.LOAD_MODULE, "frequencyHz")),
                sumRule("bus-10kv", "bus10kvVoltage",
                        "10.2 + (self.totalGenPowerKw[\"value\"] / 4440.0) * 0.6"),
                sumRule("bus-04kv", "bus04kvVoltage",
                        "0.38 + (self.totalGenPowerKw[\"value\"] / 4440.0) * 0.04"),
                BlueprintBindingRule.of("highlight-active", "highlightActive",
                        readRef(MiniTecPaths.GRPB, "fireAlarm") + " == true"),
                BlueprintBindingRule.of("equip-status", "equipmentStatus",
                        "self.alarmLatched[\"value\"] == true || self.stationUnderpower[\"value\"] == true ? \"ALARM\" : (self.loadMarginKw[\"value\"] < 200 ? \"WARNING\" : \"NORMAL\")"),
                BlueprintBindingRule.of("bus-overvoltage", "busOvervoltage", "self.bus10kvVoltage[\"value\"] > 11.0"),
                BlueprintBindingRule.of("bus-undervoltage", "busUndervoltage", "self.bus10kvVoltage[\"value\"] < 9.5"),
                BlueprintBindingRule.of("bus-freq-low", "busFrequencyLow", "self.gridFrequencyHz[\"value\"] < 49.5"),
                BlueprintBindingRule.of("bus-freq-high", "busFrequencyHigh", "self.gridFrequencyHz[\"value\"] > 50.5"),
                BlueprintBindingRule.of("station-underpower", "stationUnderpower",
                        "self.totalGenPowerKw[\"value\"] < self.totalLoadKw[\"value\"] - 100"),
                BlueprintBindingRule.of("gpu-sync-fault", "gpuSyncFault",
                        "(" + readRef(MiniTecPaths.GPU_01, "synced") + " == false && "
                                + readRef(MiniTecPaths.GPU_01, "running") + " == true) || "
                                + "(" + readRef(MiniTecPaths.GPU_02, "synced") + " == false && "
                                + readRef(MiniTecPaths.GPU_02, "running") + " == true) || "
                                + "(" + readRef(MiniTecPaths.GPU_03, "synced") + " == false && "
                                + readRef(MiniTecPaths.GPU_03, "running") + " == true)")
        );

        // Add hidden aggregate vars for gpu powers
        vars.add(meas("gpu1Power", "GPU-01 power mirror", "internal", "kW", 0));
        vars.add(meas("gpu2Power", "GPU-02 power mirror", "internal", "kW", 0));
        vars.add(meas("gpu3Power", "GPU-03 power mirror", "internal", "kW", 0));

        return model(
                HUB_MODEL,
                "Mini-TEC station hub",
                ObjectType.CUSTOM,
                vars,
                bindings,
                List.of(
                        boolEvent("busProtUndervoltage", "Bus undervoltage", EventLevel.ERROR),
                        boolEvent("stationUnderpower", "Station underpower", EventLevel.WARNING),
                        boolEvent("busProtUndervoltageCleared", "Bus undervoltage cleared", EventLevel.INFO),
                        boolEvent("stationUnderpowerCleared", "Station underpower cleared", EventLevel.INFO)
                ),
                List.of(
                        MiniTecFunctionScripts.acknowledgeAlarmFn(),
                        MiniTecFunctionScripts.aggregateDailyJournalFn()
                )
        );
    }

    private static BlueprintBindingRule refRule(String id, String target, String remotePath, String remoteVar) {
        String ref = remotePath + "/" + remoteVar;
        return new BlueprintBindingRule(
                id,
                target,
                true,
                10,
                new BindingActivators(false, List.of(BindingVariableRef.fromRef(ref)), null, 0),
                "",
                readRef(remotePath, remoteVar),
                target,
                "value"
        );
    }

    private static String readRef(String objectPath, String variableName) {
        return "read(\"" + objectPath + "/" + variableName + "\")";
    }

    private static BlueprintBindingRule sumRule(String id, String target, String expression) {
        return BlueprintBindingRule.of(id, target, expression);
    }

    private static BlueprintDefinition model(
            String name,
            String description,
            ObjectType type,
            List<BlueprintVariableDefinition> variables,
            List<BlueprintBindingRule> bindings,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions
    ) {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                name,
                description,
                BlueprintType.INSTANCE,
                type,
                "",
                variables,
                events,
                functions,
                bindings,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static BlueprintDefinition model(
            String name,
            String description,
            ObjectType type,
            List<BlueprintVariableDefinition> variables,
            List<BlueprintBindingRule> bindings,
            List<FunctionDescriptor> functions
    ) {
        return model(name, description, type, variables, bindings, List.of(), functions);
    }

    private static BlueprintDefinition model(
            String name,
            String description,
            ObjectType type,
            List<BlueprintVariableDefinition> variables,
            List<BlueprintBindingRule> bindings
    ) {
        return model(name, description, type, variables, bindings, List.of(), List.of());
    }
}
