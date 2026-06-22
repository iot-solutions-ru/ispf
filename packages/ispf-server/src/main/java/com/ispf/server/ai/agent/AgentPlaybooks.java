package com.ispf.server.ai.agent;

import com.ispf.server.dashboard.DashboardLayouts;
import com.ispf.server.plugin.model.ModelBootstrap;

/**
 * Curated multi-step recipes for the tree-first agent (referenced from system prompt).
 * <p>
 * Never use {@link String#formatted(String, Object...)} here — playbooks contain {@code %}, JSON, and paths
 * that break format strings. Use string concatenation only.
 */
public final class AgentPlaybooks {

    public static final String SNMP_DEVICE_PATH = ModelBootstrap.SNMP_LOCALHOST_PATH;
    public static final String SNMP_DASHBOARD_PATH = "root.platform.dashboards.snmp-host-monitoring";
    public static final String SNMP_MODEL = ModelBootstrap.SNMP_AGENT_MODEL;
    public static final String SNMP_DRIVER_ID = "snmp";

    private AgentPlaybooks() {
    }

    public static String snmpLocalhostMonitoring() {
        return """
                ## SNMP localhost — мониторинг ресурсов хоста
                
                Цель: устройство SNMP на 127.0.0.1:161, метрики CPU/RAM/сеть, дашборд.
                
                Шаги:
                1. search_context query="snmp localhost monitoring dashboard" (once)
                2. get_object path="""
                + SNMP_DEVICE_PATH
                + """
                 — если OK, устройство уже есть; иначе create_object:
                   parentPath=root.platform.devices, name=snmp-localhost, type=DEVICE,
                   displayName=SNMP localhost, templateId="""
                + SNMP_MODEL
                + ", driverId="
                + SNMP_DRIVER_ID
                + """
                , autoStartDriver=false
                3. set_variable path="""
                + SNMP_DEVICE_PATH
                + " name=driverConfigJson value="
                + ModelBootstrap.SNMP_DRIVER_CONFIG
                + """
                
                4. set_variable path="""
                + SNMP_DEVICE_PATH
                + " name=driverPointMappingsJson value="
                + ModelBootstrap.SNMP_POINT_MAPPINGS
                + """
                
                5. configure_driver devicePath="""
                + SNMP_DEVICE_PATH
                + " driverId="
                + SNMP_DRIVER_ID
                + """
                 autoStart=true (или driver_control action=start)
                6. list_variables path="""
                + SNMP_DEVICE_PATH
                + """
                 — показать метрики
                7. get_object path="""
                + SNMP_DASHBOARD_PATH
                + """
                 — дашборд; если нет — create_object parentPath=root.platform.dashboards,
                   name=snmp-host-monitoring, type=DASHBOARD, templateId=dashboard-v1
                8. set_dashboard_layout path="""
                + SNMP_DASHBOARD_PATH
                + """
                 template=snmp-host-monitoring
                9. finish: summary + result.devicePath + result.dashboardPath
                """;
    }

    public static String snmpDashboardLayoutHint() {
        return "Use dashboard layout compatible with selectionKey=device and variables: "
                + "sysName, sysUpTime, hrMemorySize, hrProcessorLoad, hrSystemProcesses, "
                + "hrSystemNumUsers, ifNumber, ifInOctets, ifOutOctets, status.online. "
                + "Reference length: " + DashboardLayouts.SNMP_HOST_MONITORING_DASHBOARD.length() + " chars.";
    }

    public static String dashboardLayoutEditing() {
        return """
                ## Редактирование дашборда (layout)
                
                Виджеты хранятся ТОЛЬКО в переменной layout (JSON-строка с полем widgets[]).
                Нет отдельной переменной widgets.
                
                Быстрый путь:
                1. get_dashboard_layout path="""
                + SNMP_DASHBOARD_PATH
                + """
                 — прочитать текущий layout
                2a. set_dashboard_layout path="""
                + SNMP_DASHBOARD_PATH
                + """
                 template=snmp-host-monitoring — восстановить эталон
                2b. add_dashboard_widget path="""
                + SNMP_DASHBOARD_PATH
                + """
                 widget={...} — добавить один виджет
                3. finish
                
                Пример виджета CPU (selectionKey=device, unit — символ процента):
                {"id":"cpu-value","type":"value","title":"CPU","x":0,"y":0,"w":3,"h":2,
                 "selectionKey":"device","variableName":"hrProcessorLoad","valueField":"value","unit":"pct"}
                
                Не вызывай search_context больше 1–2 раз подряд — используй get_dashboard_layout.
                """;
    }

    public static String snmpIfMibExtension() {
        return """
                ## IF-MIB — дополнительные метрики интерфейса (follow-up)
                
                Устройство: """
                + SNMP_DEVICE_PATH
                + "\nДашборд: "
                + SNMP_DASHBOARD_PATH
                + """
                
                
                Шаги (без search_context):
                1. set_variable path="""
                + SNMP_DEVICE_PATH
                + """
                 name=driverPointMappingsJson value=<JSON ниже>
                2. configure_driver devicePath="""
                + SNMP_DEVICE_PATH
                + """
                 driverId=snmp autoStart=true
                3. driver_control devicePath="""
                + SNMP_DEVICE_PATH
                + """
                 action=poll
                4. add_dashboard_widget path="""
                + SNMP_DASHBOARD_PATH
                + """
                 для ifDescr, ifSpeed, ifOperStatus, ifInErrors, ifOutErrors
                5. list_variables path="""
                + SNMP_DEVICE_PATH
                + """
                
                6. finish
                
                driverPointMappingsJson:
                """
                + ModelBootstrap.SNMP_POINT_MAPPINGS
                + "\n";
    }
}
