package com.ispf.server.driver;

import com.ispf.core.model.DataRecord;

import java.util.function.Supplier;

/**
 * Runs driver writes only on the replica that holds the device lock (cluster mode).
 */
public final class DriverWriteRouter {

    private final DriverOwnershipService ownershipService;

    public DriverWriteRouter(DriverOwnershipService ownershipService) {
        this.ownershipService = ownershipService;
    }

    /**
     * When ownership is off or this replica holds the lease, runs {@code localWrite}.
     * Otherwise fails with a clear error (cluster forward is not implemented).
     */
    public DriverRuntimeService.DriverRuntimeStatus route(
            String devicePath,
            String pointId,
            DataRecord value,
            Supplier<DriverRuntimeService.DriverRuntimeStatus> localWrite
    ) {
        if (!ownershipService.isEnabled()) {
            return localWrite.get();
        }
        if (!ownershipService.holdsLock(devicePath)) {
            String owner = ownershipService.findOwner(devicePath).orElse("unknown");
            throw new IllegalStateException(
                    "Driver write not permitted: lock held by replica "
                            + owner
                            + " (this replica is "
                            + ownershipService.instanceId()
                            + ") for "
                            + devicePath);
        }
        return localWrite.get();
    }
}
