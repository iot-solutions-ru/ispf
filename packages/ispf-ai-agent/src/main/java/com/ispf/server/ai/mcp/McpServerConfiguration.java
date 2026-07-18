package com.ispf.server.ai.mcp;

import com.ispf.server.config.McpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "ispf.mcp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(McpProperties.class)
public class McpServerConfiguration {
}
