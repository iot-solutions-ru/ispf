package com.ispf.driver.protocolstubs;

/**
 * AMQP protocol stub (amqp).
 * <p>
 * AMQP 0-9-1 / 1.0 broker stub.
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
