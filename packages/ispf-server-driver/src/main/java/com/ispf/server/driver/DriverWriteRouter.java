package com.ispf.server.driver;

import com.ispf.core.model.DataRecord;

import java.util.function.Supplier;

/**
 * Runs driver writes on the replica that holds the device lock.
 * When this replica does not hold the lease, the write is a NATS request to the owner.
 */
public final class DriverWriteRouter {

    private final DriverOwnershipService ownershipService;
    private final Supplier<DriverWriteForwarder> forwarder;

    public DriverWriteRouter(DriverOwnershipService ownershipService) {
        this(ownershipService, () -> null);
    }

    public DriverWriteRouter(
            DriverOwnershipService ownershipService,
            Supplier<DriverWriteForwarder> forwarder
    ) {
        this.ownershipService = ownershipService;
        this.forwarder = forwarder == null ? () -> null : forwarder;
    }

    /**
     * When ownership is off or this replica holds the lease, runs {@code localWrite}.
     * Otherwise asks {@link DriverWriteForwarder} to deliver the write to the owner.
     */
    public DriverRuntimeService.DriverRuntimeStatus route(
            String devicePath,
            String pointId,
            DataRecord value,
            Supplier<DriverRuntimeService.DriverRuntimeStatus> localWrite
    ) {
        if (!ownershipService.isEnabled() || ownershipService.holdsLock(devicePath)) {
            return localWrite.get();
        }
        String owner = ownershipService.findOwner(devicePath).orElse("unknown");
        String self = ownershipService.instanceId();
        DriverWriteForwarder live = forwarder.get();
        if (live != null && live.available() && !owner.equals("unknown") && !owner.equals(self)) {
            return live.forward(owner, devicePath, pointId, value);
        }
        throw new IllegalStateException(
                "Driver write not permitted: lock held by replica "
                        + owner
                        + " (this replica is "
                        + self
                        + ") for "
                        + devicePath);
    }
}
