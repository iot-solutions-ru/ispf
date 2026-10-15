package com.ispf.server.application.function;


import java.util.Locale;
public enum FunctionAuditMode {
    ERRORS,
    ALL;

    public static FunctionAuditMode parse(String value) {
        if (value == null || value.isBlank()) {
            return ERRORS;
        }
        try {
            return FunctionAuditMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ERRORS;
        }
    }
}
