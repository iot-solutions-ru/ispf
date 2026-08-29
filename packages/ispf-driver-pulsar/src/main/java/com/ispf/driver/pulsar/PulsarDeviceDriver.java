package com.ispf.driver.pulsar;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Apache Pulsar protocol stub (pulsar).
 * <p>
 * Apache Pulsar client stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class PulsarDeviceDriver extends ProtocolStubDeviceDriver {

    public PulsarDeviceDriver() {
        super(
                "pulsar",
                "Apache Pulsar Driver",
                "Apache Pulsar client stub",
                6650
        );
    }
}
