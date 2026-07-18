package com.ispf.server.ai.agent;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * BL-181: detect transient tool failures eligible for a single automatic retry.
 */
final class AgentToolTransientRetry {

    private static final Pattern TRANSIENT = Pattern.compile(
            "(?i)(timeout|timed\\s*out|temporar|unavailable|connection\\s*(reset|refused)|"
                    + "econnreset|broken\\s*pipe|503|502|504|too\\s*many\\s*requests|rate\\s*limit)"
    );

    private AgentToolTransientRetry() {
    }

    static boolean isTransient(Throwable error) {
        if (error == null) {
            return false;
        }
        if (matches(error.getMessage())) {
            return true;
        }
        Throwable cause = error.getCause();
        return cause != null && cause != error && matches(cause.getMessage());
    }

    static boolean isTransientFailure(Map<String, Object> toolResult) {
        if (toolResult == null) {
            return false;
        }
        if (!"ERROR".equals(String.valueOf(toolResult.get("status")))) {
            return false;
        }
        return matches(String.valueOf(toolResult.get("error")));
    }

    private static boolean matches(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return TRANSIENT.matcher(message.toLowerCase(Locale.ROOT)).find();
    }
}
