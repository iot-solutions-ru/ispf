package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.binding")
public class BindingProperties {

    /** Shared thread pool for async binding rule evaluation ({@code activators.async}). */
    private int asyncThreads = 16;

    public int getAsyncThreads() {
        return asyncThreads;
    }

    public void setAsyncThreads(int asyncThreads) {
        this.asyncThreads = Math.max(1, asyncThreads);
    }
}
