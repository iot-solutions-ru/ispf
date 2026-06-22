package com.ispf.server.ai.agent;

import com.ispf.server.dashboard.DashboardLayouts;
import com.ispf.server.plugin.model.ModelBootstrap;

/**
 * Curated multi-step recipes for the tree-first agent (referenced from system prompt).
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
                1. search_context query="snmp localhost monitoring dashboard"
                2. get_object path=%s — если OK, устройство уже есть; иначе create_object:
                   parentPath=root.platform.devices, name=snmp-localhost, type=DEVICE,
                   displayName=SNMP localhost, templateId=%s, driverId=%s, autoStartDriver=false
                3. set_variable path=%s name=driverConfigJson value=%s
                4. set_variable path=%s name=driverPointMappingsJson value=%s
                5. configure_driver devicePath=%s driverId=%s autoStart=true (или driver_control action=start)
                6. list_variables path=%s — показать метрики (sysName, hrMemorySize, hrProcessorLoad, ifInOctets, …)
                7. get_object path=%s — дашборд «SNMP Host Monitoring»; если нет — create_object
                   parentPath=root.platform.dashboards, name=snmp-host-monitoring, type=DASHBOARD,
                   displayName=SNMP Host Monitoring, templateId=dashboard-v1
                8. set_variable path=<dashboardPath> name=layout value=<JSON layout with selectionKey device>
                   (скопируй структуру из search_context dashboardsDoc / demo snmp-host-monitoring)
                9. set_variable path=<dashboardPath> name=title value="Мониторинг SNMP localhost"
                10. finish: summary на языке пользователя + result.devicePath + result.dashboardPath
                
                Константы платформы:
                - driverConfigJson: %s
                - driverPointMappingsJson: %s
                - demo dashboard path: %s
                """.formatted(
                SNMP_DEVICE_PATH,
                SNMP_MODEL,
                SNMP_DRIVER_ID,
                SNMP_DEVICE_PATH,
                ModelBootstrap.SNMP_DRIVER_CONFIG,
                SNMP_DEVICE_PATH,
                ModelBootstrap.SNMP_POINT_MAPPINGS,
                SNMP_DEVICE_PATH,
                SNMP_DRIVER_ID,
                SNMP_DEVICE_PATH,
                SNMP_DASHBOARD_PATH,
                ModelBootstrap.SNMP_DRIVER_CONFIG,
                ModelBootstrap.SNMP_POINT_MAPPINGS,
                SNMP_DASHBOARD_PATH
        );
    }

    public static String snmpDashboardLayoutHint() {
        return "Use dashboard layout compatible with selectionKey=device and variables: "
                + "sysName, sysUpTime, hrMemorySize, hrProcessorLoad, hrSystemProcesses, "
                + "hrSystemNumUsers, ifNumber, ifInOctets, ifOutOctets, status.online. "
                + "Reference length: " + DashboardLayouts.SNMP_HOST_MONITORING_DASHBOARD.length() + " chars.";
    }
}
