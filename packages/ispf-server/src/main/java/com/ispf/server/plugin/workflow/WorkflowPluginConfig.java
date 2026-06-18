package com.ispf.server.plugin.workflow;

import com.ispf.plugin.workflow.WorkflowEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowPluginConfig {

    @Bean
    WorkflowEngine workflowEngine() {
        return new WorkflowEngine();
    }
}
