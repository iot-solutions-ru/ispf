package com.ispf.server.bootstrap;

/**
 * Operator HMI layouts for mini-TEC (dark SCADA theme).
 */
public final class MiniTecDashboardLayouts {

    private static final String STYLES =
            "\"stylesJson\":\"{\\\"background\\\":\\\"#0d1117\\\",\\\"color\\\":\\\"#e6edf3\\\"}\"";

    public static final String OVERVIEW = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"title","type":"html-snippet","title":"Станционная сводка","x": 0,"y": 0,"w": 84,"h": 7,
            "htmlJson":"<h2 style=\\"margin:0;color:#e6edf3\\">Мини-ТЭЦ — станционная сводка</h2>"},
            {"id":"total-p","type":"value","title":"Суммарная генерация P","x": 0,"y": 7,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"totalGenPowerKw","valueField":"value","decimals":0,"unit":" kW"},
            {"id":"total-load","type":"value","title":"Суммарная нагрузка","x": 21,"y": 7,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"totalLoadKw","valueField":"value","decimals":0,"unit":" kW"},
            {"id":"margin","type":"value","title":"Резерв мощности","x": 42,"y": 7,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"loadMarginKw","valueField":"value","decimals":0,"unit":" kW"},
            {"id":"freq","type":"value","title":"Частота сети","x": 63,"y": 7,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"gridFrequencyHz","valueField":"value","decimals":2,"unit":" Hz"},
            {"id":"gpu-table","type":"object-table","title":"ГПУ","x": 0,"y": 21,"w": 56,"h": 28,
            "parentPath":"%s","objectType":"DEVICE","namePattern":"gpu-*",
            "columnsJson":"[{\\"variable\\":\\"activePowerKw\\",\\"label\\":\\"P kW\\",\\"field\\":\\"value\\"},
            {\\"variable\\":\\"running\\",\\"label\\":\\"Работа\\",\\"field\\":\\"value\\",
            \\"trueLabel\\":\\"Работает\\",\\"falseLabel\\":\\"Стоп\\"}]",
            "selectionKey":"gpu","refreshIntervalMs":3000},
            {"id":"events","type":"event-feed","title":"Журнал событий","x": 56,"y": 21,"w": 28,"h": 28,
            "objectPath":"%s","maxItems":20},
            {"id":"link-gpu","type":"dashboard-link","title":"Детали ГПУ","x": 0,"y": 49,"w": 21,"h": 7,
            "targetDashboardPath":"%s","buttonLabel":"Перейти"},
            {"id":"link-prot","type":"dashboard-link","title":"Защиты","x": 21,"y": 49,"w": 21,"h": 7,
            "targetDashboardPath":"%s","buttonLabel":"Перейти"},
            {"id":"link-grpb","type":"dashboard-link","title":"ГРПБ","x": 42,"y": 49,"w": 21,"h": 7,
            "targetDashboardPath":"%s","buttonLabel":"Перейти"}
            ]}
            """.formatted(
            STYLES,
            MiniTecPaths.STATION_HUB, MiniTecPaths.STATION_HUB, MiniTecPaths.STATION_HUB, MiniTecPaths.STATION_HUB,
            MiniTecPaths.FOLDER, MiniTecPaths.STATION_HUB,
            MiniTecPaths.DASHBOARD_GPU_DETAIL, MiniTecPaths.DASHBOARD_PROTECTIONS, MiniTecPaths.DASHBOARD_GRPB
    ).replace("\n", "").replace("  ", "");

    public static final String GPU_DETAIL = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"gpu-list","type":"object-table","title":"Выбор ГПУ","x": 0,"y": 0,"w": 84,"h": 14,
            "parentPath":"%s","namePattern":"gpu-*","selectionKey":"gpu",
            "columnsJson":"[{\\"variable\\":\\"activePowerKw\\",\\"label\\":\\"P kW\\",\\"field\\":\\"value\\"}]"},
            {"id":"power","type":"gauge","title":"Активная мощность","x": 0,"y": 14,"w": 28,"h": 21,
            "selectionKey":"gpu","variableName":"activePowerKw","min":0,"max":1600,"unit":"kW"},
            {"id":"rpm","type":"gauge","title":"Обороты","x": 28,"y": 14,"w": 28,"h": 21,
            "selectionKey":"gpu","variableName":"rpm","min":0,"max":1600,"unit":"rpm"},
            {"id":"exhaust","type":"gauge","title":"Температура выхлопа","x": 56,"y": 14,"w": 28,"h": 21,
            "selectionKey":"gpu","variableName":"exhaustGasTemp","min":0,"max":650,"unit":"°C"},
            {"id":"chart-p","type":"chart","title":"Мощность","x": 0,"y": 35,"w": 42,"h": 21,
            "selectionKey":"gpu","variableName":"activePowerKw","chartType":"line"},
            {"id":"prot","type":"indicator","title":"Перегрузка","x": 42,"y": 35,"w": 14,"h": 7,
            "selectionKey":"gpu","variableName":"protOverload","alarmMode":true,
            "trueLabel":"АВАРИЯ","falseLabel":"НОРМА"},
            {"id":"start","type":"function","title":"Пуск","x": 56,"y": 35,"w": 14,"h": 7,
            "selectionKey":"gpu","functionName":"gpu_start","buttonLabel":"Пуск"},
            {"id":"stop","type":"function","title":"Стоп","x": 70,"y": 35,"w": 14,"h": 7,
            "selectionKey":"gpu","functionName":"gpu_stop","buttonLabel":"Стоп"}
            ]}
            """.formatted(STYLES, MiniTecPaths.FOLDER).replace("\n", "").replace("  ", "");

    public static final String GRPB = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"pressure","type":"gauge","title":"Давление газа","x": 0,"y": 0,"w": 28,"h": 21,
            "objectPath":"%s","variableName":"gasOutletPressure","min":0,"max":5,"unit":"bar"},
            {"id":"flow","type":"value","title":"Расход","x": 28,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"gasFlowRate","decimals":1,"unit":" m³/h"},
            {"id":"valve","type":"linear-gauge","title":"Положение арматуры","x": 56,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"valvePosition","min":0,"max":100},
            {"id":"fire","type":"indicator","title":"Пожар","x": 0,"y": 21,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"fireAlarm","alarmMode":true,
            "trueLabel":"ПОЖАР","falseLabel":"Норма"},
            {"id":"gas","type":"indicator","title":"Загазованность","x": 21,"y": 21,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"gasLeak","alarmMode":true,
            "trueLabel":"УТЕЧКА","falseLabel":"Норма"},
            {"id":"open","type":"function","title":"Открыть","x": 42,"y": 21,"w": 21,"h": 14,
            "objectPath":"%s","functionName":"grpb_valve_control","buttonLabel":"Открыть",
            "inputJson":"{\\"action\\":\\"open\\"}"},
            {"id":"trip","type":"function","title":"Аварийный отсек","x": 63,"y": 21,"w": 21,"h": 14,
            "objectPath":"%s","functionName":"grpb_valve_control","buttonLabel":"Отсечь газ",
            "inputJson":"{\\"action\\":\\"trip\\"}"}
            ]}
            """.formatted(STYLES,
            MiniTecPaths.GRPB, MiniTecPaths.GRPB, MiniTecPaths.GRPB,
            MiniTecPaths.GRPB, MiniTecPaths.GRPB, MiniTecPaths.GRPB, MiniTecPaths.GRPB
    ).replace("\n", "").replace("  ", "");

    public static final String RUMB = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"breaker","type":"indicator","title":"Выключатель","x": 0,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"breakerClosed",
            "trueLabel":"Включён","falseLabel":"Отключён"},
            {"id":"ground","type":"indicator","title":"Заземлитель","x": 28,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"groundingSwitchClosed",
            "trueLabel":"Включён","falseLabel":"Отключён"},
            {"id":"emerg","type":"indicator","title":"Аварийный стоп","x": 56,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"emergencyStop","alarmMode":true,
            "trueLabel":"АВАРИЯ","falseLabel":"Норма"},
            {"id":"close","type":"function","title":"Включить ВК","x": 0,"y": 14,"w": 42,"h": 14,
            "objectPath":"%s","functionName":"breaker_operate","buttonLabel":"Включить",
            "inputJson":"{\\"action\\":\\"close\\"}"},
            {"id":"open","type":"function","title":"Отключить ВК","x": 42,"y": 14,"w": 42,"h": 14,
            "objectPath":"%s","functionName":"breaker_operate","buttonLabel":"Отключить",
            "inputJson":"{\\"action\\":\\"open\\"}"}
            ]}
            """.formatted(STYLES,
            MiniTecPaths.RUMB, MiniTecPaths.RUMB, MiniTecPaths.RUMB,
            MiniTecPaths.RUMB, MiniTecPaths.RUMB
    ).replace("\n", "").replace("  ", "");

    public static final String DGU = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"run","type":"indicator","title":"Работа ДГУ","x": 0,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"running",
            "trueLabel":"Работает","falseLabel":"Останов"},
            {"id":"fuel","type":"gauge","title":"Топливо","x": 28,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"fuelLevelPct","min":0,"max":100,"unit":"%%"},
            {"id":"bat","type":"indicator","title":"Заряд АКБ","x": 56,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"batteryCharging",
            "trueLabel":"Заряд","falseLabel":"Разряд"},
            {"id":"start","type":"function","title":"Пуск","x": 0,"y": 14,"w": 42,"h": 14,
            "objectPath":"%s","functionName":"dgu_start","buttonLabel":"Пуск ДГУ"},
            {"id":"stop","type":"function","title":"Стоп","x": 42,"y": 14,"w": 42,"h": 14,
            "objectPath":"%s","functionName":"dgu_stop","buttonLabel":"Стоп ДГУ"}
            ]}
            """.formatted(STYLES,
            MiniTecPaths.DGU, MiniTecPaths.DGU, MiniTecPaths.DGU, MiniTecPaths.DGU, MiniTecPaths.DGU
    ).replace("\n", "").replace("  ", "");

    public static final String LOAD_MODULE = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"p","type":"value","title":"P","x": 0,"y": 0,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"activePowerKw","decimals":0,"unit":" kW"},
            {"id":"q","type":"value","title":"Q","x": 21,"y": 0,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"reactivePowerKvar","decimals":0,"unit":" kVAr"},
            {"id":"s","type":"value","title":"S","x": 42,"y": 0,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"apparentPowerKva","decimals":0,"unit":" kVA"},
            {"id":"f","type":"value","title":"f","x": 63,"y": 0,"w": 21,"h": 14,
            "objectPath":"%s","variableName":"frequencyHz","decimals":2,"unit":" Hz"},
            {"id":"load-form","type":"function-form","title":"Установка нагрузки","x": 0,"y": 14,"w": 42,"h": 21,
            "objectPath":"%s","functionName":"load_module_set_load","buttonLabel":"Применить",
            "fieldsJson":"[{\\"name\\":\\"loadPct\\",\\"label\\":\\"Нагрузка %%\\",\\"type\\":\\"number\\"},
            {\\"name\\":\\"millMode\\",\\"label\\":\\"Режим мельницы 710 кВт\\",\\"type\\":\\"checkbox\\"}]"}
            ]}
            """.formatted(STYLES,
            MiniTecPaths.LOAD_MODULE, MiniTecPaths.LOAD_MODULE, MiniTecPaths.LOAD_MODULE,
            MiniTecPaths.LOAD_MODULE, MiniTecPaths.LOAD_MODULE
    ).replace("\n", "").replace("  ", "");

    public static final String PROTECTIONS = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"bus-ov","type":"indicator","title":"Перенапряжение шин","x": 0,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"busOvervoltage","alarmMode":true,
            "trueLabel":"АВАРИЯ","falseLabel":"НОРМА"},
            {"id":"bus-uv","type":"indicator","title":"Недонапряжение шин","x": 28,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"busUndervoltage","alarmMode":true,
            "trueLabel":"АВАРИЯ","falseLabel":"НОРМА"},
            {"id":"under","type":"indicator","title":"Дефицит мощности","x": 56,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"stationUnderpower","alarmMode":true,
            "trueLabel":"АВАРИЯ","falseLabel":"НОРМА"},
            {"id":"sync","type":"indicator","title":"Рассинхрон ГПУ","x": 0,"y": 14,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"gpuSyncFault","alarmMode":true,
            "trueLabel":"АВАРИЯ","falseLabel":"НОРМА"},
            {"id":"alarm","type":"indicator","title":"Авария зафиксирована","x": 28,"y": 14,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"alarmLatched","alarmMode":true,
            "trueLabel":"АВАРИЯ","falseLabel":"НОРМА"},
            {"id":"ack","type":"function","title":"Квитировать","x": 56,"y": 14,"w": 28,"h": 14,
            "objectPath":"%s","functionName":"acknowledge_alarm","buttonLabel":"Квитировать"}
            ]}
            """.formatted(STYLES,
            MiniTecPaths.STATION_HUB, MiniTecPaths.STATION_HUB, MiniTecPaths.STATION_HUB,
            MiniTecPaths.STATION_HUB, MiniTecPaths.STATION_HUB, MiniTecPaths.STATION_HUB
    ).replace("\n", "").replace("  ", "");

    public static final String SINGLE_LINE = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"sld","type":"scada-mimic","title":"Однолинейная схема Мини-ТЭЦ","x": 0,"y": 0,"w": 84,"h": 35,
            "mimicPath":"%s","panEnabled":true,"defaultZoom":1},
            {"id":"island","type":"indicator","title":"Островной режим","x": 0,"y": 35,"w": 28,"h": 7,
            "objectPath":"%s","variableName":"islandMode",
            "trueLabel":"Активно","falseLabel":"Параллель с сетью"},
            {"id":"margin","type":"value","title":"Резерв мощности","x": 28,"y": 35,"w": 28,"h": 7,
            "objectPath":"%s","variableName":"loadMarginKw","valueField":"value","decimals":0,"unit":" kW"},
            {"id":"freq","type":"value","title":"Частота шин","x": 56,"y": 35,"w": 28,"h": 7,
            "objectPath":"%s","variableName":"gridFrequencyHz","valueField":"value","decimals":2,"unit":" Hz"}
            ]}
            """.formatted(
            STYLES,
            MiniTecPaths.MIMIC_SINGLE_LINE,
            MiniTecPaths.STATION_HUB,
            MiniTecPaths.STATION_HUB,
            MiniTecPaths.STATION_HUB
    ).replace("\n", "").replace("  ", "");

    public static final String EXPLOITATION = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"h1","type":"value","title":"Наработка ГПУ-1","x": 0,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"runningHours","decimals":1,"unit":" ч"},
            {"id":"e1","type":"value","title":"кВт·ч ГПУ-1","x": 28,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"energyKwh","decimals":0},
            {"id":"s1","type":"value","title":"Пуски ГПУ-1","x": 56,"y": 0,"w": 28,"h": 14,
            "objectPath":"%s","variableName":"startCount","decimals":0}
            ]}
            """.formatted(STYLES, MiniTecPaths.GPU_01, MiniTecPaths.GPU_01, MiniTecPaths.GPU_01
    ).replace("\n", "").replace("  ", "");

    private MiniTecDashboardLayouts() {
    }
}
