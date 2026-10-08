package com.ispf.server.binding;


import java.util.Locale;
public enum BindingAuditMode {
    ERRORS,
    CHANGES,
    ALL;

    public static BindingAuditMode parse(String value) {
        if (value == null || value.isBlank()) {
            return CHANGES;
        }
        try {
            return BindingAuditMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return CHANGES;
        }
    }
}
