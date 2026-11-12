package com.ispf.server.driver;

import com.ispf.core.model.DataRecord;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Routes cluster driver writes to the local runtime or to the lock-holding replica.
 */
public final class DriverWriteRouter {

    private final DriverOwnershipService ownershipService;

    public DriverWriteRouter(DriverOwnershipService ownershipService) {
        this.ownershipService = ownershipService;
    }

  record ForwardRequest(
            String ownerId,
            String devicePath,
            String pointId,
            DataRecord value
    ) {
    }

    sealed interface WriteResult permits WriteResult.Local, WriteResult.Forward {

        record Local(DriverRuntimeService.DriverRuntimeStatus status) implements WriteResult {
        }

        record Forward(ForwardRequest forwardRequest) implements WriteResult {
        }
    }

    /**
     * When ownership is off or this replica holds the lease, runs {@code localWrite}.
     * Otherwise returns a forward descriptor for the current lock owner.
     */
    public WriteResult route(
            String devicePath,
            String pointId,
            DataRecord value,
            Supplier<DriverRuntimeService.DriverRuntimeStatus> localWrite
    ) {
        if (!ownershipService.isEnabled() || ownershipService.holdsLock(devicePath)) {
            return new WriteResult.Local(localWrite.get());
        }
        String ownerId = findForwardOwner(devicePath);
        return new WriteResult.Forward(new ForwardRequest(ownerId, devicePath, pointId, value));
    }

    private String findForwardOwner(String devicePath) {
        Optional<String> owner = ownershipService.findOwner(devicePath);
        return owner.orElse("unknown");
    }
}
