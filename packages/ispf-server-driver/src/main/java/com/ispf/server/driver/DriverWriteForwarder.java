package com.ispf.server.driver;

import com.ispf.core.model.DataRecord;

/**
 * Sends a driver write to the replica that holds the device lease.
 * The NATS request/reply implementation lives next to the cluster connection.
 */
public interface DriverWriteForwarder {

    boolean available();

    DriverRuntimeService.DriverRuntimeStatus forward(
            String ownerReplicaId,
            String devicePath,
            String pointId,
            DataRecord value
    );
}
