package com.ispf.server.application.reference.minitec;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;

/**
 * Function descriptors for the mini-TEC reference application.
 * Uses platform {@code pulse} and {@code script} function types — no custom {@link com.ispf.server.function.FunctionHandler}.
 */
public final class MiniTecFunctionScripts {

    public static final String DATA_SOURCE_PATH = "root.platform.data-sources.mini-tec";

    private static final DataSchema VOID_INPUT = DataSchema.builder("voidInput").build();
    private static final DataSchema ACTION_INPUT = DataSchema.builder("actionInput")
            .field("action", FieldType.STRING)
            .build();
    private static final DataSchema LOAD_INPUT = DataSchema.builder("loadInput")
            .field("loadPct", FieldType.DOUBLE)
            .field("millMode", FieldType.BOOLEAN)
            .build();
    private static final DataSchema RESULT = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private static final String BREAKER_OPERATE = """
            {"steps":[
              {"type":"when","var":"input.action","equals":"open","then":[
                {"type":"writeVariable","objectPath":"self","variable":"cmdBreakerOpen","fields":{"value":true}}
              ]},
              {"type":"when","var":"input.action","equals":"close","then":[
                {"type":"readVariable","objectPath":"self","variable":"interlockOk","field":"value","var":"interlock"},
                {"type":"when","var":"interlock","equals":"true","then":[
                  {"type":"writeVariable","objectPath":"self","variable":"cmdBreakerClose","fields":{"value":true}}
                ],"else":[
                  {"type":"return","fields":{"success":false,"message":"Interlock not OK"}}
                ]}
              ]},
              {"type":"return","fields":{"success":true,"message":"Command sent"}}
            ]}""";

    private static final String GRPB_VALVE = """
            {"steps":[
              {"type":"when","var":"input.action","equals":"open","then":[
                {"type":"writeVariable","objectPath":"self","variable":"cmdValveOpen","fields":{"value":true}}
              ]},
              {"type":"when","var":"input.action","equals":"close","then":[
                {"type":"writeVariable","objectPath":"self","variable":"cmdValveClose","fields":{"value":true}}
              ]},
              {"type":"when","var":"input.action","equals":"trip","then":[
                {"type":"writeVariable","objectPath":"self","variable":"cmdGasTrip","fields":{"value":true}}
              ]},
              {"type":"return","fields":{"success":true,"message":"Command sent"}}
            ]}""";

    private static final String ACKNOWLEDGE_ALARM = """
            {"steps":[
              {"type":"writeVariable","objectPath":"self","variable":"alarmLatched","fields":{"value":false}},
              {"type":"writeVariable","objectPath":"self","variable":"millLoadPending","fields":{"value":false}},
              {"type":"return","fields":{"success":true,"message":"Alarm acknowledged"}}
            ]}""";

    private MiniTecFunctionScripts() {
    }

    public static FunctionDescriptor pulse(String name, String description, String variable) {
        return new FunctionDescriptor(
                name,
                description,
                VOID_INPUT,
                RESULT,
                "pulse",
                "{\"variable\":\"" + variable + "\"}",
                null,
                null
        );
    }

    public static FunctionDescriptor script(
            String name,
            String description,
            DataSchema input,
            String sourceBody
    ) {
        return script(name, description, input, sourceBody, null);
    }

    public static FunctionDescriptor script(
            String name,
            String description,
            DataSchema input,
            String sourceBody,
            String dataSourcePath
    ) {
        return new FunctionDescriptor(
                name,
                description,
                input,
                RESULT,
                "script",
                sourceBody,
                dataSourcePath,
                null
        );
    }

    public static FunctionDescriptor gpuStartFn() {
        return pulse("gpu_start", "Start GPU", "cmdStart");
    }

    public static FunctionDescriptor gpuStopFn() {
        return pulse("gpu_stop", "Stop GPU", "cmdStop");
    }

    public static FunctionDescriptor gpuSyncFn() {
        return pulse("gpu_sync", "Synchronize GPU", "cmdSync");
    }

    public static FunctionDescriptor dguStartFn() {
        return pulse("dgu_start", "Start DGU", "cmdStart");
    }

    public static FunctionDescriptor dguStopFn() {
        return pulse("dgu_stop", "Stop DGU", "cmdStop");
    }

    public static FunctionDescriptor grpbPzkResetFn() {
        return pulse("grpb_pzk_reset", "Reset PZK", "cmdPzkReset");
    }

    public static FunctionDescriptor simulateFireFn() {
        return pulse("simulate_fire", "Simulate GRPB fire", "cmdSimulateFire");
    }

    public static FunctionDescriptor simulateGasLeakFn() {
        return pulse("simulate_gas_leak", "Simulate GRPB gas leak", "cmdSimulateGasLeak");
    }

    public static FunctionDescriptor grpbValveFn() {
        return script("grpb_valve_control", "GRPB valve control", ACTION_INPUT, GRPB_VALVE);
    }

    public static FunctionDescriptor breakerOperateFn() {
        return script("breaker_operate", "Operate breaker", ACTION_INPUT, BREAKER_OPERATE);
    }

    public static FunctionDescriptor loadModuleSetLoadFn() {
        return script(
                "load_module_set_load",
                "Set load module",
                LOAD_INPUT,
                loadModuleScript()
        );
    }

    public static FunctionDescriptor acknowledgeAlarmFn() {
        return script("acknowledge_alarm", "Acknowledge alarm", VOID_INPUT, ACKNOWLEDGE_ALARM);
    }

    public static FunctionDescriptor aggregateDailyJournalFn() {
        return script(
                "aggregate_daily_journal",
                "Aggregate daily journal",
                VOID_INPUT,
                aggregateJournalScript(),
                DATA_SOURCE_PATH
        );
    }

    private static String loadModuleScript() {
        return """
                {"steps":[
                  {"type":"setVar","var":"loadPct","value":"${input.loadPct}"},
                  {"type":"when","var":"input.millMode","equals":"true","then":[
                    {"type":"writeVariable","objectPath":"%s","variable":"millLoadPending","fields":{"value":true}}
                  ]},
                  {"type":"writeVariable","objectPath":"self","variable":"loadSetpointPct","fields":{"value":"${loadPct}","unit":"%%"}},
                  {"type":"writeVariable","objectPath":"self","variable":"cmdSetLoad","fields":{"value":true}},
                  {"type":"return","fields":{"success":true,"message":"Load setpoint applied"}}
                ]}""".formatted(MiniTecPaths.STATION_HUB);
    }

    private static String aggregateJournalScript() {
        return """
                {"steps":[
                  {"type":"readVariable","objectPath":"%s","variable":"energyKwh","field":"value","var":"e1"},
                  {"type":"readVariable","objectPath":"%s","variable":"energyKwh","field":"value","var":"e2"},
                  {"type":"readVariable","objectPath":"%s","variable":"energyKwh","field":"value","var":"e3"},
                  {"type":"readVariable","objectPath":"%s","variable":"reactiveEnergyKvarh","field":"value","var":"r1"},
                  {"type":"readVariable","objectPath":"%s","variable":"reactiveEnergyKvarh","field":"value","var":"r2"},
                  {"type":"readVariable","objectPath":"%s","variable":"reactiveEnergyKvarh","field":"value","var":"r3"},
                  {"type":"readVariable","objectPath":"%s","variable":"runningHours","field":"value","var":"h1"},
                  {"type":"readVariable","objectPath":"%s","variable":"runningHours","field":"value","var":"h2"},
                  {"type":"readVariable","objectPath":"%s","variable":"runningHours","field":"value","var":"h3"},
                  {"type":"readVariable","objectPath":"%s","variable":"startCount","field":"value","var":"s1"},
                  {"type":"readVariable","objectPath":"%s","variable":"startCount","field":"value","var":"s2"},
                  {"type":"readVariable","objectPath":"%s","variable":"startCount","field":"value","var":"s3"},
                  {"type":"setVar","var":"totalEnergy","expression":"${e1} + ${e2} + ${e3}"},
                  {"type":"setVar","var":"totalReactive","expression":"${r1} + ${r2} + ${r3}"},
                  {"type":"setVar","var":"totalHours","expression":"${h1} + ${h2} + ${h3}"},
                  {"type":"setVar","var":"startCount","expression":"${s1} + ${s2} + ${s3}"},
                  {"type":"exec","sql":"INSERT INTO tec_daily_journal (journal_date, total_energy_kwh, total_reactive_kvarh, total_running_hours, start_count) VALUES (CURRENT_DATE, ?, ?, ?, ?) ON CONFLICT (journal_date) DO UPDATE SET total_energy_kwh = EXCLUDED.total_energy_kwh, total_reactive_kvarh = EXCLUDED.total_reactive_kvarh, total_running_hours = EXCLUDED.total_running_hours, start_count = EXCLUDED.start_count","params":["${totalEnergy}","${totalReactive}","${totalHours}","${startCount}"]},
                  {"type":"readVariable","objectPath":"self","variable":"totalGenPowerKw","field":"value","var":"genKw"},
                  {"type":"setVar","var":"availability","expression":"${genKw} / 44.4"},
                  {"type":"writeVariable","objectPath":"self","variable":"plantAvailabilityPct","fields":{"value":"${availability}","unit":"%%"}},
                  {"type":"return","fields":{"success":true,"message":"Daily journal updated"}}
                ]}""".formatted(
                MiniTecPaths.GPU_01,
                MiniTecPaths.GPU_02,
                MiniTecPaths.GPU_03,
                MiniTecPaths.GPU_01,
                MiniTecPaths.GPU_02,
                MiniTecPaths.GPU_03,
                MiniTecPaths.GPU_01,
                MiniTecPaths.GPU_02,
                MiniTecPaths.GPU_03,
                MiniTecPaths.GPU_01,
                MiniTecPaths.GPU_02,
                MiniTecPaths.GPU_03
        );
    }
}
