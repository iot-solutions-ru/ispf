package com.ispf.driver.thread;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread Border Router gateway lab point.
 * <p>
 * Forms: {@code ip:fd00::1}, {@code udp:61631}, {@code child:1}.
 */
record ThreadPoint(Kind kind, String display, String ip, int portOrChild) {

    enum Kind {
        IP,
        UDP,
        CHILD
    }

    private static final Pattern IP = Pattern.compile(
            "^ip\\s*[:=]\\s*([0-9A-Fa-f:]+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern UDP = Pattern.compile(
            "^udp\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CHILD = Pattern.compile(
            "^child\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    static ThreadPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Thread point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher ip = IP.matcher(trimmed);
        if (ip.matches()) {
            String addr = ip.group(1).toLowerCase(Locale.ROOT);
            if (!addr.contains(":")) {
                throw new DriverException("Thread ip point requires IPv6-style address: " + mapping);
            }
            return new ThreadPoint(Kind.IP, "ip:" + addr, addr, -1);
        }
        Matcher udp = UDP.matcher(trimmed);
        if (udp.matches()) {
            int port = Integer.parseInt(udp.group(1));
            if (port < 1 || port > 65535) {
                throw new DriverException("Thread udp port out of range: " + port);
            }
            return new ThreadPoint(Kind.UDP, "udp:" + port, null, port);
        }
        Matcher child = CHILD.matcher(trimmed);
        if (child.matches()) {
            int index = Integer.parseInt(child.group(1));
            if (index < 1) {
                throw new DriverException("Thread child index out of range: " + index);
            }
            return new ThreadPoint(Kind.CHILD, "child:" + index, null, index);
        }
        throw new DriverException(
                "Unsupported Thread mapping (expected ip:fd00::1, udp:61631, or child:1): " + mapping);
    }

    boolean writable() {
        return kind == Kind.IP || kind == Kind.UDP;
    }

    String wireToken() {
        return display;
    }

    String kindToken() {
        return switch (kind) {
            case IP -> "ip";
            case UDP -> "udp";
            case CHILD -> "child";
        };
    }
}
