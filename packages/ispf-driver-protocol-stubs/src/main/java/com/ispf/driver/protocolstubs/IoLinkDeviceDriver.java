package com.ispf.driver.protocolstubs;

/**
 * IO-Link protocol stub (io-link).
 * <p>
 * IO-Link master REST/MQTT bridge stub.
 */
public class IoLinkDeviceDriver extends ProtocolStubDeviceDriver {

    public IoLinkDeviceDriver() {
        super(
                "io-link",
                "IO-Link Driver",
                "IO-Link master REST/MQTT bridge stub",
                8080
        );
    }
}
