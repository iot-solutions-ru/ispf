package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.mqtt-gateway")
public class MqttGatewayProperties {

    /** Worker threads for parallel per-topic ingress dispatch. */
    private int ingressDispatchThreads = 8;

    public int getIngressDispatchThreads() {
        return ingressDispatchThreads;
    }

    public void setIngressDispatchThreads(int ingressDispatchThreads) {
        this.ingressDispatchThreads = Math.max(1, ingressDispatchThreads);
    }
}
