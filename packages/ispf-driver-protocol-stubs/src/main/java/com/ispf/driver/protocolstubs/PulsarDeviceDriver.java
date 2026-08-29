package com.ispf.driver.protocolstubs;

/**
 * Apache Pulsar protocol stub (pulsar).
 * <p>
 * Apache Pulsar client stub.
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
