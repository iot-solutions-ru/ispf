package com.ispf.server.workflow;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Checks the workflow fields a later read parses again: instance JSON and the last-run instant.
 * Blank text is left for the reader, which treats it as absent.
 */
public final class WorkflowRuntimeFields {

    private static final ObjectMapper JSON = new ObjectMapper();

    private WorkflowRuntimeFields() {
    }

    public static void requireReadable(String name, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if ("instanceState".equals(name)) {
            try {
                JSON.readTree(text);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Invalid instanceState", ex);
            }
            return;
        }
        if ("lastRunAt".equals(name)) {
            try {
                Instant.parse(text);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Invalid lastRunAt", ex);
            }
        }
    }
}
