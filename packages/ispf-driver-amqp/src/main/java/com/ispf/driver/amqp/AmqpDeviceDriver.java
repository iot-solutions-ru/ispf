package com.ispf.driver.amqp;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * AMQP protocol stub (amqp).
 * <p>
 * AMQP 0-9-1 / 1.0 broker stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class AmqpDeviceDriver extends ProtocolStubDeviceDriver {

    public AmqpDeviceDriver() {
        super(
                "amqp",
                "AMQP Driver",
                "AMQP 0-9-1 / 1.0 broker stub",
                5672
        );
    }
}
