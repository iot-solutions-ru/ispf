package com.ispf.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@org.springframework.boot.context.properties.EnableConfigurationProperties({
        com.ispf.server.config.NatsProperties.class,
        com.ispf.server.config.IspfSecurityProperties.class,
        com.ispf.server.config.VariableHistoryProperties.class,
        com.ispf.server.config.VariableHistorySloProperties.class,
        com.ispf.server.config.HistorianTierProperties.class,
        com.ispf.server.config.HistorianColdArchiveProperties.class,
        com.ispf.server.config.MlAnomalyProperties.class,
        com.ispf.server.config.TenantIsolationProperties.class,
        com.ispf.server.config.AlarmShelfProperties.class,
        com.ispf.server.config.PlatformUpdateProperties.class,
        com.ispf.server.config.CommercialLicenseProperties.class,
        com.ispf.server.config.AiProperties.class,
        com.ispf.server.config.McpProperties.class,
        com.ispf.server.config.DriverPackProperties.class,
        com.ispf.server.config.ReportYargProperties.class,
        com.ispf.server.config.RuntimeTelemetryProperties.class,
        com.ispf.server.config.ObjectChangeProperties.class,
        com.ispf.server.config.EventJournalProperties.class,
        com.ispf.server.config.PlatformMetricsProbeProperties.class,
        com.ispf.server.config.IspfRedisProperties.class,
        com.ispf.server.config.MqttGatewayProperties.class,
        com.ispf.server.config.BindingProperties.class,
        com.ispf.server.config.FunctionProperties.class,
        com.ispf.server.config.BootstrapProperties.class,
        com.ispf.server.config.NotificationProperties.class,
        com.ispf.server.config.ClusterProperties.class,
        com.ispf.server.config.AnalyticsProperties.class,
        com.ispf.server.config.AnalyticsPackProperties.class,
        com.ispf.server.config.AnalyticsSloProperties.class,
        com.ispf.server.config.WebSocketProperties.class,
        com.ispf.server.config.MarketplaceProperties.class,
        com.ispf.server.config.MetadataDbProperties.class,
        com.ispf.server.config.ExternalDataSourceProperties.class,
        com.ispf.server.federation.FederationOutboundBufferProperties.class,
        com.ispf.server.agent.AgentStoreForwardProperties.class
})
public class IspfServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IspfServerApplication.class, args);
    }
}
