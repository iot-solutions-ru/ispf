package com.ispf.driver.asterisk;

/**
 * Point mapping: AMI command block, e.g. {@code Action: Ping}.
 */
public record AsteriskPoint(String command) {

    public static AsteriskPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Asterisk AMI point mapping is blank");
        }
        return new AsteriskPoint(raw.trim());
    }

    public String toAmiMessage() {
        if (command.contains("\r\n")) {
            return command.endsWith("\r\n\r\n") ? command : command + "\r\n\r\n";
        }
        return command + "\r\n\r\n";
    }
}
