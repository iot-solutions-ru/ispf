package com.ispf.server.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards admin-configured outbound HTTP URLs (federation peer login) against SSRF.
 * Site-local / LAN hosts stay allowed (typical OT federation). Loopback and cloud
 * metadata endpoints are restricted.
 *
 * <p>After validation the URI is rebuilt from discrete components so static analysis
 * (CodeQL {@code java/ssrf}) treats the returned value as an untainted HTTP sink input.
 */
public final class OutboundUrlSafety {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private OutboundUrlSafety() {
    }

    public static URI requireSafeHttpUrl(String rawUrl, String allowlistCsv, boolean blockLoopback) {
        return requireSafeHttpUrl(rawUrl, allowlistCsv, blockLoopback, false);
    }

    /**
     * HTTPS-only outbound URL that also blocks RFC1918 / ULA hosts (git pull / push SSRF).
     */
    public static URI requireSafePublicHttpsUrl(String rawUrl) {
        URI uri = requireSafeHttpUrl(rawUrl, "", true, true);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL scheme must be https");
        }
        return uri;
    }

    public static URI requireSafeHttpUrl(
            String rawUrl,
            String allowlistCsv,
            boolean blockLoopback,
            boolean blockPrivateAddresses
    ) {
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
        if (blockPrivateAddresses && isPrivateHostnameLiteral(normalizedHost)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "URL host is a private address and is blocked for outbound git calls: " + host
            );
        }
        if (resolvesToBlockedAddress(normalizedHost, blockLoopback, allowlist, blockPrivateAddresses)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "URL host resolves to a blocked address for outbound calls: " + host
            );
        }
        return rebuild(scheme, normalizedHost, uri.getPort(), uri.getRawPath(), uri.getRawQuery(), uri.getRawFragment());
    }

    /** Strip trailing '/' without regex (avoids polynomial ReDoS on user URLs). */
    public static String stripTrailingSlashes(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    /**
     * Resolve a path/query against an already-validated base URI so HTTP sinks use the
     * validated {@link URI} object rather than a re-parsed string concat.
     */
    public static URI resolvePath(URI safeBase, String pathAndQuery) {
        if (safeBase == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required");
        }
        if (pathAndQuery == null || pathAndQuery.isBlank()) {
            return safeBase;
        }
        String base = safeBase.toASCIIString();
        URI baseWithSlash = base.endsWith("/") ? safeBase : URI.create(base + "/");
        String relative = pathAndQuery.startsWith("/") ? pathAndQuery.substring(1) : pathAndQuery;
        URI resolved = baseWithSlash.resolve(relative);
        if (!Objects.equals(safeBase.getScheme(), resolved.getScheme())
                || !Objects.equals(safeBase.getHost(), resolved.getHost())
                || safeBase.getPort() != resolved.getPort()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolved URL must keep the validated host");
        }
        String scheme = resolved.getScheme() == null ? "" : resolved.getScheme().toLowerCase(Locale.ROOT);
        return rebuild(
                scheme,
                resolved.getHost(),
                resolved.getPort(),
                resolved.getRawPath(),
                resolved.getRawQuery(),
                resolved.getRawFragment()
        );
    }

    /**
     * Like {@link #resolvePath} but keeps already-percent-encoded segments
     * ({@code %2F} stays {@code %2F}; the multi-arg URI constructor would double-encode).
     * Returns {@link URI#create} result after a same-origin check — do not rebuild.
     */
    public static URI resolveEncodedPath(URI safeBase, String encodedPathAndQuery) {
        if (safeBase == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required");
        }
        if (encodedPathAndQuery == null || encodedPathAndQuery.isBlank()) {
            return safeBase;
        }
        String base = safeBase.toASCIIString();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        String relative = encodedPathAndQuery.startsWith("/")
                ? encodedPathAndQuery.substring(1)
                : encodedPathAndQuery;
        URI resolved = URI.create(base + relative);
        if (!Objects.equals(safeBase.getScheme(), resolved.getScheme())
                || !Objects.equals(safeBase.getHost(), resolved.getHost())
                || safeBase.getPort() != resolved.getPort()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolved URL must keep the validated host");
        }
        return resolved;
    }

    /**
     * Rebuild from discrete components after validation — breaks CodeQL taint into HTTP sinks.
     */
    private static URI rebuild(String scheme, String host, int port, String path, String query, String fragment) {
        try {
            return new URI(scheme, null, host, port, path, query, fragment);
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL: " + ex.getMessage());
        }
    }

    static boolean isCloudMetadataHost(String host) {
        return "metadata.google.internal".equals(host)
                || "metadata".equals(host)
                || host.endsWith(".metadata.google.internal");
    }

    static boolean resolvesToBlockedAddress(String host, boolean blockLoopback, Set<String> allowlist) {
        return resolvesToBlockedAddress(host, blockLoopback, allowlist, false);
    }

    static boolean resolvesToBlockedAddress(
            String host,
            boolean blockLoopback,
            Set<String> allowlist,
            boolean blockPrivateAddresses
    ) {
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
                if (blockPrivateAddresses && isPrivateInetAddress(address)) {
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

    static boolean isPrivateHostnameLiteral(String host) {
        return isPrivateInetLiteral(host);
    }

    private static boolean isPrivateInetAddress(InetAddress address) {
        if (address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            return true;
        }
        return isPrivateInetLiteral(address.getHostAddress());
    }

    /** RFC-1918 IPv4 (10/8, 172.16/12, 192.168/16) and IPv6 ULA (fc00::/7). */
    private static boolean isPrivateInetLiteral(String host) {
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (h.startsWith("::ffff:")) {
            h = h.substring("::ffff:".length());
        }
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (h.matches("f[cd][0-9a-f]{0,2}:.*")) {
            return true;
        }
        String[] parts = h.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168);
        } catch (NumberFormatException ex) {
            return false;
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
