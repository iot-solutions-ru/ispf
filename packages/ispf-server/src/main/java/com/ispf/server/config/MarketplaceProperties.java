package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ispf.marketplace")
public class MarketplaceProperties {

    private boolean enabled = true;
    private String defaultId = "iot-solutions";
    /** Local dev/lab bundle directory, e.g. examples/marketplace-demo (BL-183). */
    private String localBundlesDir = "";
    private List<Endpoint> endpoints = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultId() {
        return defaultId;
    }

    public void setDefaultId(String defaultId) {
        this.defaultId = defaultId;
    }

    public String getLocalBundlesDir() {
        return localBundlesDir;
    }

    public void setLocalBundlesDir(String localBundlesDir) {
        this.localBundlesDir = localBundlesDir != null ? localBundlesDir : "";
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints != null ? endpoints : new ArrayList<>();
    }

    public static class Endpoint {
        private String id = "";
        private String name = "";
        /** Base URL, e.g. https://marketplace.iot-solutions.ru */
        private String baseUrl = "";
        private String contactUrl = "";
        private boolean defaultEndpoint;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getContactUrl() {
            return contactUrl;
        }

        public void setContactUrl(String contactUrl) {
            this.contactUrl = contactUrl;
        }

        public boolean isDefaultEndpoint() {
            return defaultEndpoint;
        }

        public void setDefaultEndpoint(boolean defaultEndpoint) {
            this.defaultEndpoint = defaultEndpoint;
        }
    }
}
