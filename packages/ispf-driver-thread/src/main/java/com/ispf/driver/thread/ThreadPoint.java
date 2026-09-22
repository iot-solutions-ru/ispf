package com.ispf.driver.thread;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * Thread Spinel host-bridge point (CMD_RESET subset).
 * <p>
 * Forms: {@code reset}, {@code cmd:reset}.
 */
record ThreadPoint(String display) {

    static ThreadPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Thread point mapping is blank");
        }
        String trimmed = mapping.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("reset".equals(lower) || "cmd:reset".equals(lower) || "cmd=reset".equals(lower)) {
            return new ThreadPoint("reset");
        }
        throw new DriverException(
                "Unsupported Thread Spinel mapping (expected reset or cmd:reset): " + mapping);
    }

    String kindToken() {
        return "reset";
    }
}
