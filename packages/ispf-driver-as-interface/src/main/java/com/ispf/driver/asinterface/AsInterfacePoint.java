package com.ispf.driver.asinterface;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AS-Interface point addressing for master-call telegrams.
 * <p>
 * Forms: {@code slave:3}, {@code slave:3:di0}, {@code slave:3:do1}.
 */
record AsInterfacePoint(int slave, Channel channel, int bit) {

    enum Channel {
        AGGREGATE,
        DI,
        DO
    }

    private static final Pattern MAPPING = Pattern.compile(
            "^slave\\s*[:=]\\s*(\\d+)(?:\\s*[:=]\\s*(di|do)\\s*(\\d+))?$",
            Pattern.CASE_INSENSITIVE);

    static AsInterfacePoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("AS-Interface point mapping is blank");
        }
        Matcher matcher = MAPPING.matcher(mapping.trim());
        if (!matcher.matches()) {
            throw new DriverException(
                    "Unsupported AS-Interface mapping (expected slave:3 or slave:3:di0/do1): " + mapping);
        }
        int slave = Integer.parseInt(matcher.group(1));
        if (slave < 0 || slave > 62) {
            throw new DriverException("AS-Interface slave out of range: " + slave);
        }
        if (matcher.group(2) == null) {
            return new AsInterfacePoint(slave, Channel.AGGREGATE, -1);
        }
        Channel channel = "do".equalsIgnoreCase(matcher.group(2)) ? Channel.DO : Channel.DI;
        int bit = Integer.parseInt(matcher.group(3));
        if (bit < 0 || bit > 7) {
            throw new DriverException("AS-Interface bit out of range: " + bit);
        }
        return new AsInterfacePoint(slave, channel, bit);
    }

    int wireAddress() throws DriverException {
        if (slave < 0 || slave > 31) {
            throw new DriverException("AS-Interface address out of 5-bit range: " + slave);
        }
        return slave;
    }

    int readCommand() {
        return switch (channel) {
            case AGGREGATE -> 0x00;
            case DI -> 0x10 | (bit & 0x07);
            case DO -> 0x20 | (bit & 0x07);
        };
    }

    int writeData(double value) {
        int discrete = ((int) Math.round(value)) & 0x7F;
        return switch (channel) {
            case AGGREGATE -> discrete;
            case DO -> (discrete != 0 ? 1 : 0) << (bit & 0x07);
            case DI -> discrete;
        };
    }

    String wireToken() {
        return switch (channel) {
            case AGGREGATE -> "slave:" + slave;
            case DI -> "slave:" + slave + ":di" + bit;
            case DO -> "slave:" + slave + ":do" + bit;
        };
    }

    boolean writable() {
        return channel == Channel.AGGREGATE || channel == Channel.DO;
    }

    String channelLabel() {
        return switch (channel) {
            case AGGREGATE -> "aggregate";
            case DI -> "di";
            case DO -> "do";
        };
    }

    String display() {
        return wireToken().toLowerCase(Locale.ROOT);
    }
}
