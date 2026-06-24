package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.object-change")
public class ObjectChangeProperties {

    /**
     * When true, heavy {@code ObjectChangeEvent} listeners run on {@code objectChangeExecutor}.
     * When false, {@link ObjectChangeAsyncConfig} is not loaded and {@code @Async} is ignored
     * (listeners run synchronously on the publishing thread).
     */
    private boolean asyncEnabled = true;

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }
}
