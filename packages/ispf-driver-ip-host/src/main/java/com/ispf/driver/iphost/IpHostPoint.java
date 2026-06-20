package com.ispf.driver.iphost;

import java.util.Locale;

/**
 * Point mapping: {@code MODE:target[:port]} where MODE is PING|HTTP|TCP|DNS|SMTP|FTP.
 */
public record IpHostPoint(IpHostMode mode, String target, int port) {

    public static IpHostPoint parse(String raw, String defaultHost) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("IP host point mapping is blank");
        }
        String trimmed = raw.trim();
        int firstColon = trimmed.indexOf(':');
        if (firstColon <= 0) {
            throw new IllegalArgumentException("Expected MODE:target[:port] but got: " + raw);
        }
        IpHostMode mode = IpHostMode.valueOf(trimmed.substring(0, firstColon).trim().toUpperCase(Locale.ROOT));
        String remainder = trimmed.substring(firstColon + 1).trim();
        if (remainder.isEmpty()) {
            remainder = defaultHost;
        }
        int port;
        String target;
        int lastColon = remainder.lastIndexOf(':');
        if (lastColon > 0 && mode.usesPort()) {
            String maybePort = remainder.substring(lastColon + 1).trim();
            try {
                port = Integer.parseInt(maybePort);
                target = remainder.substring(0, lastColon).trim();
            } catch (NumberFormatException e) {
                target = remainder;
                port = mode.defaultPort();
            }
        } else {
            target = remainder;
            port = mode.defaultPort();
        }
        if (target.isEmpty()) {
            target = defaultHost;
        }
        return new IpHostPoint(mode, target, port);
    }

    public enum IpHostMode {
        PING(0),
        HTTP(80),
        TCP(80),
        DNS(0),
        SMTP(25),
        FTP(21);

        private final int defaultPort;

        IpHostMode(int defaultPort) {
            this.defaultPort = defaultPort;
        }

        public int defaultPort() {
            return defaultPort;
        }

        public boolean usesPort() {
            return this != PING && this != DNS;
        }
    }
}
