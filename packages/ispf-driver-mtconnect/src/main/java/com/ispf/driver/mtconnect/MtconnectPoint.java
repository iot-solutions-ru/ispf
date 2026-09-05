package com.ispf.driver.mtconnect;

import java.util.Locale;

/**
 * Point selector for an MTConnect data item id or name.
 * <p>
 * Accepted forms: {@code x_pos}, {@code Xact}, {@code id:x_pos}, {@code name:Xact}.
 */
record MtconnectPoint(String selector, boolean nameOnly, boolean idOnly) {

    static MtconnectPoint parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("MTConnect point mapping must not be blank");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("name:")) {
            String name = text.substring(5).trim();
            if (name.isBlank()) {
                throw new IllegalArgumentException("MTConnect name: mapping requires a name");
            }
            return new MtconnectPoint(name, true, false);
        }
        if (lower.startsWith("id:")) {
            String id = text.substring(3).trim();
            if (id.isBlank()) {
                throw new IllegalArgumentException("MTConnect id: mapping requires a dataItemId");
            }
            return new MtconnectPoint(id, false, true);
        }
        return new MtconnectPoint(text, false, false);
    }

    String key() {
        String normalized = selector.toLowerCase(Locale.ROOT);
        if (nameOnly) {
            return "name:" + normalized;
        }
        if (idOnly) {
            return "id:" + normalized;
        }
        return normalized;
    }
}
