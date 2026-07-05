package com.ispf.server.config;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ADR-0032: resolved effective capabilities for this JVM.
 */
public final class ReplicaCapabilitySet {

    private final ReplicaProfile profile;
    private final Set<ReplicaCapability> capabilities;

    private ReplicaCapabilitySet(ReplicaProfile profile, Set<ReplicaCapability> capabilities) {
        this.profile = profile;
        this.capabilities = EnumSet.copyOf(capabilities);
    }

    public ReplicaProfile profile() {
        return profile;
    }

    public Set<ReplicaCapability> capabilities() {
        return EnumSet.copyOf(capabilities);
    }

    public boolean has(ReplicaCapability capability) {
        return capabilities.contains(capability);
    }

    public List<String> externalNames() {
        return capabilities.stream()
                .map(ReplicaCapability::externalName)
                .sorted()
                .collect(Collectors.toList());
    }

    public String serialized() {
        return String.join(",", externalNames());
    }

    public static ReplicaCapabilitySet resolve(
            String profileRaw,
            String roleRaw,
            String capabilitiesRaw
    ) {
        ReplicaProfile profile;
        Set<ReplicaCapability> base;
        if (capabilitiesRaw != null && !capabilitiesRaw.isBlank()) {
            profile = profileRaw != null && !profileRaw.isBlank()
                    ? ReplicaProfile.parse(profileRaw)
                    : ReplicaProfile.UNIFIED;
            base = parseExplicit(capabilitiesRaw);
        } else if (profileRaw != null && !profileRaw.isBlank()) {
            profile = ReplicaProfile.parse(profileRaw);
            base = profile.capabilities();
        } else if (roleRaw != null && !roleRaw.isBlank()) {
            profile = ReplicaProfile.parse(roleRaw);
            base = profile.capabilities();
        } else {
            profile = ReplicaProfile.UNIFIED;
            base = profile.capabilities();
        }
        return new ReplicaCapabilitySet(profile, expandImplicit(base));
    }

    public static ReplicaCapabilitySet unified() {
        return new ReplicaCapabilitySet(ReplicaProfile.UNIFIED, EnumSet.allOf(ReplicaCapability.class));
    }

    private static Set<ReplicaCapability> parseExplicit(String raw) {
        EnumSet<ReplicaCapability> parsed = EnumSet.noneOf(ReplicaCapability.class);
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(ReplicaCapability::parse)
                .forEach(parsed::add);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("ISPF_REPLICA_CAPABILITIES must list at least one capability");
        }
        return parsed;
    }

    private static Set<ReplicaCapability> expandImplicit(Set<ReplicaCapability> base) {
        EnumSet<ReplicaCapability> expanded = EnumSet.copyOf(base);
        if (expanded.contains(ReplicaCapability.DRIVERS)
                || expanded.contains(ReplicaCapability.WS)
                || expanded.contains(ReplicaCapability.HTTP_PUBLIC)) {
            expanded.add(ReplicaCapability.REPLICA_SYNC);
        }
        if (expanded.contains(ReplicaCapability.WS) && !expanded.contains(ReplicaCapability.HTTP_PUBLIC)) {
            // read-only HMI tier still needs public HTTP for REST refetch
            expanded.add(ReplicaCapability.HTTP_PUBLIC);
        }
        validate(expanded);
        return expanded;
    }

    private static void validate(Set<ReplicaCapability> capabilities) {
        if (capabilities.contains(ReplicaCapability.JOBS) && capabilities.contains(ReplicaCapability.DRIVERS)) {
            throw new IllegalArgumentException(
                    "Replica capabilities must not combine jobs and drivers on one JVM");
        }
        if (capabilities.contains(ReplicaCapability.WS) && !capabilities.contains(ReplicaCapability.REPLICA_SYNC)) {
            throw new IllegalArgumentException("ws capability requires replica-sync");
        }
        if (capabilities.contains(ReplicaCapability.HTTP_PUBLIC) && !capabilities.contains(ReplicaCapability.REPLICA_SYNC)) {
            throw new IllegalArgumentException("http-public capability requires replica-sync");
        }
    }
}
