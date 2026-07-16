package com.ispf.server.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards admin-configured outbound HTTP URLs (federation peer login) against SSRF.
 * Site-local / LAN hosts stay allowed (typical OT federation). Loopback and cloud
 * metadata endpoints are restricted.
 */
public final class OutboundUrlSafety {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private OutboundUrlSafety() {
    }

    public static URI requireSafeHttpUrl(String rawUrl, String allowlistCsv, boolean blockLoopback) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL: " + ex.getMessage());
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL scheme must be http or https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL host is required");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        Set<String> allowlist = parseAllowlist(allowlistCsv);
        if (!allowlist.isEmpty() && !hostAllowed(normalizedHost, allowlist)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "URL host is not in ispf.federation.outbound-url-allowlist: " + host
            );
        }
        if (isCloudMetadataHost(normalizedHost)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "URL host is blocked for outbound federation calls: " + host
            );
        }
        if (blockLoopback && isLoopbackHostname(normalizedHost) && (allowlist.isEmpty() || !hostAllowed(normalizedHost, allowlist))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "URL host is blocked for outbound federation calls: " + host
            );
        }
        if (resolvesToBlockedAddress(normalizedHost, blockLoopback, allowlist)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "URL host resolves to a blocked address for outbound federation calls: " + host
            );
        }
        return uri;
    }

    static boolean isCloudMetadataHost(String host) {
        return "metadata.google.internal".equals(host)
                || "metadata".equals(host)
                || host.endsWith(".metadata.google.internal");
    }

    static boolean resolvesToBlockedAddress(String host, boolean blockLoopback, Set<String> allowlist) {
        if (!allowlist.isEmpty() && hostAllowed(host, allowlist) && isLoopbackHostname(host)) {
            // Explicit allowlist can permit loopback for lab federation.
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isMetadataAddress(address)) {
                    return true;
                }
                if (blockLoopback && (address.isLoopbackAddress() || address.isAnyLocalAddress())) {
                    return true;
                }
                if (address.isLinkLocalAddress() || address.isMulticastAddress()) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException ex) {
            // Unresolvable hostnames are allowed at config time; connect will fail later.
            // Still apply hostname-level loopback block when requested.
            return blockLoopback && isLoopbackHostname(host);
        }
    }

    private static boolean isLoopbackHostname(String host) {
        return "localhost".equals(host)
                || host.endsWith(".localhost")
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    private static boolean isMetadataAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4
                && (bytes[0] & 0xff) == 169
                && (bytes[1] & 0xff) == 254
                && (bytes[2] & 0xff) == 169
                && (bytes[3] & 0xff) == 254;
    }

    private static Set<String> parseAllowlist(String allowlistCsv) {
        if (allowlistCsv == null || allowlistCsv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowlistCsv.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(part -> part.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hostAllowed(String host, Set<String> allowlist) {
        for (String pattern : allowlist) {
            if (pattern.startsWith("*.") && host.endsWith(pattern.substring(1))) {
                return true;
            }
            if (pattern.equals(host)) {
                return true;
            }
        }
        return false;
    }
}
