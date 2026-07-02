package com.ispf.server.application.bundle;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * BL-97: strict {@code MAJOR.MINOR.PATCH} semver for bundle manifests.
 */
public final class BundleSemverSupport {

    private static final Pattern SEMVER = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    private BundleSemverSupport() {
    }

    public record ParsedVersion(int major, int minor, int patch, String raw) {
    }

    public static void requireValid(String version) {
        if (!isValid(version)) {
            throw new IllegalArgumentException(
                    "manifest.version must be semver MAJOR.MINOR.PATCH (e.g. \"1.0.0\"), got: "
                            + (version == null ? "null" : "\"" + version + "\"")
            );
        }
    }

    public static boolean isValid(String version) {
        return parse(version).isPresent();
    }

    public static Optional<ParsedVersion> parse(String version) {
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }
        var matcher = SEMVER.matcher(version.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                version.trim()
        ));
    }

    public static Optional<String> majorBumpWarning(String activeVersion, String incomingVersion) {
        Optional<ParsedVersion> active = parse(activeVersion);
        Optional<ParsedVersion> incoming = parse(incomingVersion);
        if (active.isEmpty() || incoming.isEmpty()) {
            return Optional.empty();
        }
        if (incoming.get().major() > active.get().major()) {
            return Optional.of(
                    "Major version bump (" + active.get().raw() + " → " + incoming.get().raw()
                            + "): review migrations and operator UI breaking changes before deploy."
            );
        }
        return Optional.empty();
    }

    public static String changelog(ApplicationBundleDeployService.BundleManifest manifest) {
        if (manifest == null) {
            return "";
        }
        if (manifest.metadata() != null) {
            Object raw = manifest.metadata().get("changelog");
            if (raw != null && !String.valueOf(raw).isBlank()) {
                return String.valueOf(raw).trim();
            }
        }
        return "";
    }
}
