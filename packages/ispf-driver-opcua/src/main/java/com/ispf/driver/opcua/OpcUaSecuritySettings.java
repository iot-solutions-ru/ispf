package com.ispf.driver.opcua;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

/**
 * OPC UA channel security selected from driver config.
 */
record OpcUaSecuritySettings(SecurityPolicy policy, MessageSecurityMode mode, String pkiDir) {

    static OpcUaSecuritySettings none() {
        return new OpcUaSecuritySettings(SecurityPolicy.None, MessageSecurityMode.None, "");
    }

    boolean secure() {
        return policy != SecurityPolicy.None && mode != MessageSecurityMode.None;
    }

    static OpcUaSecuritySettings parse(String policyRaw, String modeRaw, String pkiDirRaw) {
        SecurityPolicy policy = parsePolicy(policyRaw);
        MessageSecurityMode mode = parseMode(modeRaw, policy);
        String pkiDir = pkiDirRaw == null ? "" : pkiDirRaw.trim();
        if (policy == SecurityPolicy.None) {
            mode = MessageSecurityMode.None;
        } else if (mode == MessageSecurityMode.None) {
            throw new IllegalArgumentException(
                    "securityMode None requires securityPolicy None"
            );
        }
        return new OpcUaSecuritySettings(policy, mode, pkiDir);
    }

    static SecurityPolicy parsePolicy(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw.trim())) {
            return SecurityPolicy.None;
        }
        String normalized = raw.trim();
        if (normalized.startsWith("SecurityPolicy.")) {
            normalized = normalized.substring("SecurityPolicy.".length());
        }
        for (SecurityPolicy candidate : SecurityPolicy.values()) {
            if (candidate.name().equalsIgnoreCase(normalized)
                    || candidate.name().replace('_', '-').equalsIgnoreCase(normalized)
                    || candidate.getUri().equalsIgnoreCase(normalized)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported securityPolicy: " + raw);
    }

    static MessageSecurityMode parseMode(String raw, SecurityPolicy policy) {
        if (raw == null || raw.isBlank()) {
            return policy == SecurityPolicy.None
                    ? MessageSecurityMode.None
                    : MessageSecurityMode.SignAndEncrypt;
        }
        String normalized = raw.trim();
        if ("none".equalsIgnoreCase(normalized)) {
            return MessageSecurityMode.None;
        }
        if ("sign".equalsIgnoreCase(normalized)) {
            return MessageSecurityMode.Sign;
        }
        if ("signandencrypt".equalsIgnoreCase(normalized)
                || "SignAndEncrypt".equalsIgnoreCase(normalized)
                || "sign-and-encrypt".equalsIgnoreCase(normalized)) {
            return MessageSecurityMode.SignAndEncrypt;
        }
        throw new IllegalArgumentException("Unsupported securityMode: " + raw);
    }
}
