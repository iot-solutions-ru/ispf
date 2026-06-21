package com.ispf.server.config;

import com.ispf.expression.PlatformBindingRegistry;
import com.ispf.server.object.ObjectBindingStatePort;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BindingStateConfig {

    private final ObjectBindingStatePort objectBindingStatePort;

    public BindingStateConfig(ObjectBindingStatePort objectBindingStatePort) {
        this.objectBindingStatePort = objectBindingStatePort;
    }

    @PostConstruct
    void wireBindingStatePort() {
        PlatformBindingRegistry.setBindingStatePort(objectBindingStatePort);
    }
}
