package com.ispf.driver.nats;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * NATS protocol stub (nats).
 * <p>
 * NATS messaging stub (cluster messaging is separate).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class NatsDeviceDriver extends ProtocolStubDeviceDriver {

    public NatsDeviceDriver() {
        super(
                "nats",
                "NATS Driver",
                "NATS messaging stub (cluster messaging is separate)",
                4222
        );
    }
}
