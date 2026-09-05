package com.ispf.driver.controlnet;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ControlNet CIP gateway lab point.
 * <p>
 * Forms: {@code slot:0}, {@code slot:0:ch:1}, {@code node:2}.
 */
record ControlnetPoint(Kind kind, int slot, int channel, int node) {

    enum Kind {
        SLOT,
        SLOT_CHANNEL,
        NODE
    }

    private static final Pattern SLOT_CHANNEL = Pattern.compile(
            "^slot\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*ch\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOT_ONLY = Pattern.compile(
            "^slot\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NODE_ONLY = Pattern.compile(
            "^node\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

    static ControlnetPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("ControlNet lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher slotChannel = SLOT_CHANNEL.matcher(trimmed);
        if (slotChannel.matches()) {
            int slot = Integer.parseInt(slotChannel.group(1));
            int channel = Integer.parseInt(slotChannel.group(2));
            validateSlot(slot);
            if (channel < 0 || channel > 255) {
                throw new DriverException("ControlNet lab channel out of range: " + channel);
            }
            return new ControlnetPoint(Kind.SLOT_CHANNEL, slot, channel, 0);
        }
        Matcher slotOnly = SLOT_ONLY.matcher(trimmed);
        if (slotOnly.matches()) {
            int slot = Integer.parseInt(slotOnly.group(1));
            validateSlot(slot);
            return new ControlnetPoint(Kind.SLOT, slot, 0, 0);
        }
        Matcher nodeOnly = NODE_ONLY.matcher(trimmed);
        if (nodeOnly.matches()) {
            int node = Integer.parseInt(nodeOnly.group(1));
            if (node < 0 || node > 99) {
                throw new DriverException("ControlNet lab node out of range: " + node);
            }
            return new ControlnetPoint(Kind.NODE, 0, 0, node);
        }
        throw new DriverException(
                "Unsupported ControlNet lab mapping (expected slot:0, slot:0:ch:1, or node:2): "
                        + mapping);
    }

    private static void validateSlot(int slot) throws DriverException {
        if (slot < 0 || slot > 16) {
            throw new DriverException("ControlNet lab slot out of range: " + slot);
        }
    }

    String wireToken() {
        return switch (kind) {
            case SLOT -> "slot:" + slot;
            case SLOT_CHANNEL -> "slot:" + slot + ":ch:" + channel;
            case NODE -> "node:" + node;
        };
    }

    String display() {
        return wireToken().toLowerCase(Locale.ROOT);
    }
}
