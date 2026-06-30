package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.license")
public class CommercialLicenseProperties {

    private String dataDir = "./data";

    /** PEM-encoded RSA public key(s); empty disables signature verify. Multiple PEM blocks supported for key rotation. */
    private String publicKeyPem = "";

    /** When true, invalid commercial licenses block startup, bundle deploy, and licensed driver packs. */
    private boolean enforce = false;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public boolean isEnforce() {
        return enforce;
    }

    public void setEnforce(boolean enforce) {
        this.enforce = enforce;
    }
}
