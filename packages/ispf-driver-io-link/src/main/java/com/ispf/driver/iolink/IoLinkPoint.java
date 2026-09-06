package com.ispf.driver.iolink;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IO-Link master lab point.
 * <p>
 * Forms: {@code port:1}, {@code port:1:pdin}, {@code port:1:pdout}.
 */
record IoLinkPoint(int port, Channel channel) {

    enum Channel {
        PORT,
        PDIN,
        PDOUT
    }

    private static final Pattern MAPPING = Pattern.compile(
            "^port\\s*[:=]\\s*(\\d+)(?:\\s*[:=]\\s*(pdin|pdout))?$",
            Pattern.CASE_INSENSITIVE);

    static IoLinkPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("IO-Link point mapping is blank");
        }
        Matcher matcher = MAPPING.matcher(mapping.trim());
        if (!matcher.matches()) {
            throw new DriverException(
                    "Unsupported IO-Link mapping (expected port:1 or port:1:pdin/pdout): " + mapping);
        }
        int port = Integer.parseInt(matcher.group(1));
        if (port < 1 || port > 255) {
            throw new DriverException("IO-Link port out of range: " + port);
        }
        if (matcher.group(2) == null) {
            return new IoLinkPoint(port, Channel.PORT);
        }
        Channel channel = "pdout".equalsIgnoreCase(matcher.group(2)) ? Channel.PDOUT : Channel.PDIN;
        return new IoLinkPoint(port, channel);
    }

    boolean writable() {
        return channel == Channel.PORT || channel == Channel.PDOUT;
    }

    String channelLabel() {
        return switch (channel) {
            case PORT -> "port";
            case PDIN -> "pdin";
            case PDOUT -> "pdout";
        };
    }

    String display() {
        String token = switch (channel) {
            case PORT -> "port:" + port;
            case PDIN -> "port:" + port + ":pdin";
            case PDOUT -> "port:" + port + ":pdout";
        };
        return token.toLowerCase(Locale.ROOT);
    }
}
