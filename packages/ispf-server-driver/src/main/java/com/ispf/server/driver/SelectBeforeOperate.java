package com.ispf.server.driver;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Select-before-operate (SBO) arm window for SCADA control commands.
 */
public final class SelectBeforeOperate {

    private final Clock clock;
    private final ConcurrentHashMap<Key, ArmState> arms = new ConcurrentHashMap<>();

    public SelectBeforeOperate() {
        this(Clock.systemUTC());
    }

    SelectBeforeOperate(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void arm(String devicePath, String pointId, Duration timeout) {
        requireDeviceAndPoint(devicePath, pointId);
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        Instant expiresAt = clock.instant().plus(timeout);
        arms.put(new Key(devicePath, pointId), new ArmState(expiresAt, false));
    }

  /**
     * Consumes the arm when within the timeout; only one successful operate per arm.
     */
    public void operate(String devicePath, String pointId) {
        requireDeviceAndPoint(devicePath, pointId);
        Key key = new Key(devicePath, pointId);
        ArmState state = arms.get(key);
        if (state == null || state.consumed()) {
            throw new IllegalStateException("SBO not armed for " + devicePath + " / " + pointId);
        }
        Instant now = clock.instant();
        if (now.isAfter(state.expiresAt())) {
            arms.remove(key, state);
            throw new IllegalStateException("SBO arm expired for " + devicePath + " / " + pointId);
        }
        if (!arms.replace(key, state, new ArmState(state.expiresAt(), true))) {
            throw new IllegalStateException("SBO not armed for " + devicePath + " / " + pointId);
        }
    }

    private static void requireDeviceAndPoint(String devicePath, String pointId) {
        if (devicePath == null || devicePath.isBlank()) {
            throw new IllegalArgumentException("devicePath is required");
        }
        if (pointId == null || pointId.isBlank()) {
            throw new IllegalArgumentException("pointId is required");
        }
    }

    private record Key(String devicePath, String pointId) {
    }

    private record ArmState(Instant expiresAt, boolean consumed) {
    }
}
