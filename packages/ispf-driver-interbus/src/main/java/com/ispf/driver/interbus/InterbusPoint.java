package com.ispf.driver.interbus;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * INTERBUS gateway lab point.
 * <p>
 * Forms: {@code slot:1}, {@code word:0}, {@code slot:1:word:0}.
 */
record InterbusPoint(int slot, int word) {

    private static final Pattern SLOT_WORD = Pattern.compile(
            "^slot\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*word\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOT_ONLY = Pattern.compile(
            "^slot\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_ONLY = Pattern.compile(
            "^word\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    static InterbusPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("INTERBUS point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher slotWord = SLOT_WORD.matcher(trimmed);
        if (slotWord.matches()) {
            return create(Integer.parseInt(slotWord.group(1)), Integer.parseInt(slotWord.group(2)));
        }
        Matcher slotOnly = SLOT_ONLY.matcher(trimmed);
        if (slotOnly.matches()) {
            return create(Integer.parseInt(slotOnly.group(1)), 0);
        }
        Matcher wordOnly = WORD_ONLY.matcher(trimmed);
        if (wordOnly.matches()) {
            return create(0, Integer.parseInt(wordOnly.group(1)));
        }
        throw new DriverException(
                "Unsupported INTERBUS mapping (expected slot:1, word:0, or slot:1:word:0): " + mapping);
    }

    private static InterbusPoint create(int slot, int word) throws DriverException {
        if (slot < 0 || slot > 255) {
            throw new DriverException("INTERBUS slot out of range: " + slot);
        }
        if (word < 0 || word > 255) {
            throw new DriverException("INTERBUS word out of range: " + word);
        }
        return new InterbusPoint(slot, word);
    }

    String wireToken() {
        return "slot:" + slot + ":word:" + word;
    }

    String display() {
        return wireToken().toLowerCase(Locale.ROOT);
    }
}
