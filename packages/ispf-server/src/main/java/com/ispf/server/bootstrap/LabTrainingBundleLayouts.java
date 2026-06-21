package com.ispf.server.bootstrap;

/**
 * Dashboard layout JSON for {@code examples/lab-training/bundle.json}.
 */
public final class LabTrainingBundleLayouts {

    public static final String LAB_DEVICE_A = "root.platform.devices.lab-userA-01";
    public static final String LAB_DEVICE_B = "root.platform.devices.lab-userB-01";

    public static final String FORM_GRID = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"append-row","type":"function-form","title":"Append table row","x":0,"y":0,"w":6,"h":4,"objectPath":"%s","functionName":"appendTableRow","buttonLabel":"Append","fieldsJson":"[{\\"name\\":\\"int\\",\\"label\\":\\"Int\\",\\"type\\":\\"number\\"},{\\"name\\":\\"string\\",\\"label\\":\\"String\\",\\"type\\":\\"text\\"}]"}]}
            """.formatted(LAB_DEVICE_A).trim();

    public static final String CALCULATOR = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"calc-form","type":"function-form","title":"Calculator","x":0,"y":0,"w":5,"h":4,"objectPath":"%s","functionName":"calculate","buttonLabel":"Calculate","fieldsJson":"[{\\"name\\":\\"inputA\\",\\"label\\":\\"A\\",\\"type\\":\\"number\\"},{\\"name\\":\\"inputB\\",\\"label\\":\\"B\\",\\"type\\":\\"number\\"}]"},{"id":"calc-result","type":"value","title":"Result","x":5,"y":0,"w":3,"h":2,"objectPath":"%s","variableName":"sumIntFloat","valueField":"value","decimals":2}]}
            """.formatted(LAB_DEVICE_A, LAB_DEVICE_A).trim();

    public static final String EVENT_GEN = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"fire-event1","type":"function-form","title":"Fire event 1","x":0,"y":0,"w":4,"h":4,"objectPath":"%s","functionName":"fireEvent1","buttonLabel":"Fire event1","fieldsJson":"[{\\"name\\":\\"int\\",\\"label\\":\\"Int\\",\\"type\\":\\"number\\"},{\\"name\\":\\"string\\",\\"label\\":\\"String\\",\\"type\\":\\"text\\"}]"},{"id":"fire-event2","type":"function-form","title":"Fire event 2","x":4,"y":0,"w":4,"h":4,"objectPath":"%s","functionName":"fireEvent2","buttonLabel":"Fire event2","fieldsJson":"[{\\"name\\":\\"int\\",\\"label\\":\\"Int\\",\\"type\\":\\"number\\"},{\\"name\\":\\"string\\",\\"label\\":\\"String\\",\\"type\\":\\"text\\"}]"},{"id":"event1-feed","type":"event-feed","title":"Event 1 log","x":0,"y":4,"w":6,"h":4,"objectPath":"%s","eventNamesJson":"[\\"event1\\"]","maxItems":20},{"id":"event2-feed","type":"event-feed","title":"Event 2 log (filtered)","x":6,"y":4,"w":6,"h":4,"objectPath":"%s","eventNamesJson":"[\\"event2\\"]","payloadFilterExpr":"int > 10 || string contains abc","maxItems":20}]}
            """.formatted(LAB_DEVICE_A, LAB_DEVICE_A, LAB_DEVICE_A, LAB_DEVICE_A).trim();

    public static final String CHARTS = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"sine-chart","type":"chart","title":"Sine wave","x":0,"y":0,"w":6,"h":4,"objectPath":"%s","variableName":"sineWave","valueField":"value","chartStyle":"line","maxPoints":120,"historyRange":"live","refreshIntervalMs":1000,"color":"#2f81f7","decimals":2},{"id":"saw-chart","type":"chart","title":"Sawtooth wave","x":6,"y":0,"w":6,"h":4,"objectPath":"%s","variableName":"sawtoothWave","valueField":"value","chartStyle":"line","maxPoints":120,"historyRange":"live","color":"#3fb950","decimals":2}]}
            """.formatted(LAB_DEVICE_A, LAB_DEVICE_A).trim();

    public static final String PIE = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"table-pie","type":"pie-chart","title":"Table int distribution","x":0,"y":0,"w":12,"h":5,"objectPath":"%s","variableName":"table","labelField":"string","valueField":"int","decimals":0}]}
            """.formatted(LAB_DEVICE_A).trim();

    public static final String MODAL = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"open-charts","type":"dashboard-link","title":"Open charts","x":0,"y":0,"w":4,"h":2,"targetDashboardPath":"root.platform.dashboards.lab-charts","openMode":"modal","buttonLabel":"Open charts modal","modalTitle":"Lab charts"}]}
            """.trim();

    public static final String HISTORY = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"sine-history-table","type":"history-table","title":"Sine history (5 min)","x":0,"y":0,"w":12,"h":5,"objectPath":"%s","variableName":"sineWave","valueField":"value","decimals":2}]}
            """.formatted(LAB_DEVICE_A).trim();

    public static final String FAN_COMPOSITE = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"fan-composite","type":"composite-widget","title":"Fan control","x":0,"y":0,"w":6,"h":4,"objectPath":"%s","childrenJson":"[{\\"id\\":\\"fan-button\\",\\"type\\":\\"svg-widget\\",\\"title\\":\\"Start fan\\",\\"svgUrl\\":\\"/lab-assets/button.svg\\",\\"clickAction\\":\\"toggle\\",\\"objectPath\\":\\"%s\\",\\"toggleVariable\\":\\"fanRunning\\",\\"valueField\\":\\"value\\"},{\\"id\\":\\"fan-blades\\",\\"type\\":\\"svg-widget\\",\\"title\\":\\"Fan\\",\\"svgUrl\\":\\"/lab-assets/fan.svg\\",\\"objectPath\\":\\"%s\\"}]"},{"id":"alarm-latched","type":"indicator","title":"Alarm latched","x":6,"y":0,"w":3,"h":2,"objectPath":"%s","variableName":"alarmLatched","valueField":"value","trueLabel":"Latched","falseLabel":"Clear"}]}
            """.formatted(LAB_DEVICE_A, LAB_DEVICE_A, LAB_DEVICE_A, LAB_DEVICE_A).trim();

    public static final String VIRTUAL_OVERVIEW = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"sine-value","type":"value","title":"Sine","x":0,"y":0,"w":3,"h":2,"objectPath":"%s","variableName":"sineWave","valueField":"value","decimals":2},{"id":"sum-waves","type":"value","title":"Sum waves","x":3,"y":0,"w":3,"h":2,"objectPath":"%s","variableName":"sumWaves","valueField":"value","decimals":2},{"id":"table-sum","type":"value","title":"Table int sum","x":6,"y":0,"w":3,"h":2,"objectPath":"%s","variableName":"tableIntSum","valueField":"value"},{"id":"events-feed","type":"event-feed","title":"Events","x":0,"y":2,"w":6,"h":4,"objectPath":"%s","maxItems":15},{"id":"devices-table","type":"report","title":"All devices table","x":6,"y":2,"w":6,"h":4,"reportPath":"root.platform.reports.lab-all-devices-table"}]}
            """.formatted(LAB_DEVICE_A, LAB_DEVICE_A, LAB_DEVICE_A, LAB_DEVICE_A).trim();

    public static final String VARIABLE_EDITOR = """
            {"columns":12,"rowHeight":72,"widgets":[{"id":"editor-a","type":"variable-editor","title":"Lab user A variables","x":0,"y":0,"w":6,"h":5,"objectPath":"%s","variablesJson":"[\\"intValue\\",\\"floatValue\\",\\"sumIntFloat\\"]"},{"id":"editor-b","type":"variable-editor","title":"Lab user B variables","x":6,"y":0,"w":6,"h":5,"objectPath":"%s","variablesJson":"[\\"intValue\\",\\"floatValue\\",\\"sumIntFloat\\"]"}]}
            """.formatted(LAB_DEVICE_A, LAB_DEVICE_B).trim();

    private LabTrainingBundleLayouts() {
    }
}
