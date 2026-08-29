package com.ispf.driver.protocolstubs;

/**
 * NATS protocol stub (nats).
 * <p>
 * NATS messaging stub (cluster messaging is separate).
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
